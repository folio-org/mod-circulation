package api.requests;

import static api.support.PubsubPublisherTestUtils.assertThatPublishedLogRecordEventsAreValid;
import static api.support.builders.RequestBuilder.CLOSED_PICKUP_EXPIRED;
import static api.support.builders.RequestBuilder.OPEN_NOT_YET_FILLED;
import static api.support.fakes.PublishedEvents.byLogEventType;
import static api.support.matchers.PatronNoticeMatcher.hasEmailNoticeProperties;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.waitAtMost;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE;
import static org.folio.circulation.support.ClockManager.getClockManager;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.joda.time.DateTimeZone.UTC;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.folio.circulation.domain.policy.Period;
import org.hamcrest.Matcher;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import api.support.APITests;
import api.support.builders.CheckInByBarcodeRequestBuilder;
import api.support.builders.HoldingBuilder;
import api.support.builders.ItemBuilder;
import api.support.builders.NoticeConfigurationBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.builders.RequestBuilder;
import api.support.fakes.FakePubSub;
import api.support.fixtures.ItemExamples;
import api.support.fixtures.TemplateContextMatchers;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import io.vertx.core.json.JsonObject;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class RequestScheduledNoticesProcessingTests extends APITests {
  private final UUID templateId = UUID.randomUUID();
  private ItemResource item;
  private IndividualResource requester;
  private IndividualResource pickupServicePoint;

  @Before
  public void beforeEach() {
    FakePubSub.clearPublishedEvents();

    ItemBuilder itemBuilder = ItemExamples.basedUponSmallAngryPlanet(
      materialTypesFixture.book().getId(), loanTypesFixture.canCirculate().getId());
    HoldingBuilder holdingBuilder = itemsFixture.applyCallNumberHoldings(
      "CN",
      "Prefix",
      "Suffix",
      singletonList("CopyNumbers"));

    item = itemsFixture.basedUponSmallAngryPlanet(itemBuilder, holdingBuilder);
    requester = usersFixture.steve();
    pickupServicePoint = servicePointsFixture.cd1();
  }

  /**
   * method name starting with a for @FixMethodOrder(MethodSorters.NAME_ASCENDING) .
   * FIXME: remove the cause that make this method fail when executed after the others of this class.
   */
  @Test
  public void aUponAtRequestExpirationNoticeShouldBeSentAndDeletedWhenRequestExpirationDateHasPassed() {
    JsonObject noticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withRequestExpirationEvent()
      .withUponAtTiming()
      .sendInRealTime(true)
      .create();
    setupNoticePolicyWithRequestNotice(noticeConfiguration);

    final var requestExpiration = LocalDate.now(getClockManager().getClock()).minusDays(1);

    IndividualResource request = requestsFixture.place(new RequestBuilder().page()
      .forItem(item)
      .withRequesterId(requester.getId())
      .withRequestDate(DateTime.now())
      .withStatus(OPEN_NOT_YET_FILLED)
      .withPickupServicePoint(pickupServicePoint)
      .withRequestExpiration(requestExpiration));

    waitAtMost(1, SECONDS)
      .until(scheduledNoticesClient::getAll, hasSize(1));

    //close request
    IndividualResource requestInStorage = requestsStorageClient.get(request);

    requestsStorageClient.replace(request.getId(),
      requestInStorage.getJson().put("status", "Closed - Unfilled"));

    scheduledNoticeProcessingClient.runRequestNoticesProcessing();

    assertThat(scheduledNoticesClient.getAll(), empty());

    final var notices = patronNoticesClient.getAll();

    assertThat(notices, hasSize(1));
    assertThat(notices.get(0), getTemplateContextMatcher(templateId, request));

    assertThat(FakePubSub.getPublishedEventsAsList(byLogEventType(NOTICE.value())), hasSize(1));
    assertThatPublishedLogRecordEventsAreValid();
  }

  @Test
  public void uponAtRequestExpirationNoticeShouldNotBeSentWhenRequestExpirationDateHasPassedAndRequestIsNotClosed() {
    JsonObject noticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withRequestExpirationEvent()
      .withUponAtTiming()
      .sendInRealTime(true)
      .create();
    setupNoticePolicyWithRequestNotice(noticeConfiguration);

    final var requestExpiration = LocalDate.now(getClockManager().getClock()).minusDays(1);

    requestsFixture.place(new RequestBuilder().page()
      .forItem(item)
      .withRequesterId(requester.getId())
      .withRequestDate(DateTime.now())
      .withStatus(OPEN_NOT_YET_FILLED)
      .withPickupServicePoint(pickupServicePoint)
      .withRequestExpiration(requestExpiration));

    waitAtMost(1, SECONDS)
      .until(scheduledNoticesClient::getAll, hasSize(1));

    scheduledNoticeProcessingClient.runRequestNoticesProcessing();

    assertThat(scheduledNoticesClient.getAll(), hasSize(1));
  }

  @Test
  public void uponAtHoldExpirationNoticeShouldBeSentAndDeletedWhenHoldExpirationDateHasPassed() {
    JsonObject noticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withHoldShelfExpirationEvent()
      .withUponAtTiming()
      .sendInRealTime(true)
      .create();

    setupNoticePolicyWithRequestNotice(noticeConfiguration);

    IndividualResource request = requestsFixture.place(new RequestBuilder().page()
      .forItem(item)
      .withRequesterId(requester.getId())
      .withRequestDate(DateTime.now())
      .withStatus(OPEN_NOT_YET_FILLED)
      .withPickupServicePoint(pickupServicePoint));

    CheckInByBarcodeRequestBuilder builder = new CheckInByBarcodeRequestBuilder()
      .forItem(item)
      .withItemBarcode(item.getBarcode())
      .at(pickupServicePoint);
    checkInFixture.checkInByBarcode(builder);

    waitAtMost(1, SECONDS)
      .until(scheduledNoticesClient::getAll, hasSize(1));

    //close request
    requestsClient.replace(request.getId(),
      request.getJson().put("status", "Closed - Pickup expired"));
    scheduledNoticeProcessingClient.runRequestNoticesProcessing(
      org.joda.time.LocalDate.now(UTC).plusDays(31).toDateTimeAtStartOfDay());

    assertThat(scheduledNoticesClient.getAll(), empty());
  }

  @Test
  public void uponAtHoldExpirationNoticeShouldNotBeSentWhenHoldExpirationDateHasPassedAndRequestIsNotClosed() {
    JsonObject noticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withHoldShelfExpirationEvent()
      .withUponAtTiming()
      .sendInRealTime(true)
      .create();
    setupNoticePolicyWithRequestNotice(noticeConfiguration);

    requestsFixture.place(new RequestBuilder().page()
      .forItem(item)
      .withRequesterId(requester.getId())
      .withRequestDate(DateTime.now())
      .withStatus(OPEN_NOT_YET_FILLED)
      .withPickupServicePoint(pickupServicePoint));

    CheckInByBarcodeRequestBuilder builder = new CheckInByBarcodeRequestBuilder()
      .forItem(item)
      .withItemBarcode(item.getBarcode())
      .at(pickupServicePoint);
    checkInFixture.checkInByBarcode(builder);

    waitAtMost(1, SECONDS)
      .until(scheduledNoticesClient::getAll, hasSize(1));

    scheduledNoticeProcessingClient.runRequestNoticesProcessing(
      org.joda.time.LocalDate.now(UTC).plusDays(31).toDateTimeAtStartOfDay());

    assertThat(scheduledNoticesClient.getAll(), hasSize(1));
  }

  @Test
  public void uponAtHoldExpirationNoticeShouldNotBeSentWhenHoldExpirationDateHasPassedAndItemCheckedOut() {
    JsonObject noticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withHoldShelfExpirationEvent()
      .withUponAtTiming()
      .sendInRealTime(true)
      .create();
    setupNoticePolicyWithRequestNotice(noticeConfiguration);

    IndividualResource request = requestsFixture.place(new RequestBuilder().page()
      .forItem(item)
      .withRequesterId(requester.getId())
      .withRequestDate(DateTime.now())
      .withStatus(OPEN_NOT_YET_FILLED)
      .withPickupServicePoint(pickupServicePoint));

    CheckInByBarcodeRequestBuilder builder = new CheckInByBarcodeRequestBuilder()
      .forItem(item)
      .withItemBarcode(item.getBarcode())
      .at(pickupServicePoint);
    checkInFixture.checkInByBarcode(builder);

    waitAtMost(1, SECONDS)
      .until(scheduledNoticesClient::getAll, hasSize(1));

    assertThat(patronNoticesClient.getAll(), empty());

    checkOutFixture.checkOutByBarcode(item, requester);

    waitAtMost(1, SECONDS)
      .until(() -> requestsClient.get(request.getId()).getJson().getString("status"),
        equalTo("Closed - Filled"));

    scheduledNoticeProcessingClient.runRequestNoticesProcessing(
      org.joda.time.LocalDate.now(UTC).plusDays(100).toDateTimeAtStartOfDay());

    assertThat(scheduledNoticesClient.getAll(), empty());
    assertThat(patronNoticesClient.getAll(), empty());
  }

  @Test
  public void beforeRequestExpirationNoticeShouldBeSentAndDeletedWhenIsNotRecurring() {
    JsonObject noticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withRequestExpirationEvent()
      .withBeforeTiming(Period.days(5))
      .sendInRealTime(true)
      .create();
    setupNoticePolicyWithRequestNotice(noticeConfiguration);

    final var requestExpiration = LocalDate.now(getClockManager().getClock()).plusDays(4);

    IndividualResource request = requestsFixture.place(new RequestBuilder().page()
      .forItem(item)
      .withRequesterId(requester.getId())
      .withRequestDate(DateTime.now())
      .withStatus(OPEN_NOT_YET_FILLED)
      .withPickupServicePoint(pickupServicePoint)
      .withRequestExpiration(requestExpiration));

    waitAtMost(1, SECONDS)
      .until(scheduledNoticesClient::getAll, hasSize(1));

    scheduledNoticeProcessingClient.runRequestNoticesProcessing();

    final var notices = patronNoticesClient.getAll();

    assertThat(notices, hasSize(1));
    assertThat(notices.get(0), getTemplateContextMatcher(templateId, request));

    assertThat(FakePubSub.getPublishedEventsAsList(byLogEventType(NOTICE.value())), hasSize(1));
    assertThatPublishedLogRecordEventsAreValid();
  }

  @Test
  public void beforeRequestExpirationRecurringNoticeShouldBeSentAndUpdatedWhenFirstThreholdBeforeExpirationHasPassed() {
    JsonObject noticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withRequestExpirationEvent()
      .withBeforeTiming(Period.days(3))
      .recurring(Period.days(1))
      .sendInRealTime(true)
      .create();
    setupNoticePolicyWithRequestNotice(noticeConfiguration);

    final var requestExpiration = LocalDate.now(getClockManager().getClock()).plusDays(2);

    IndividualResource request = requestsFixture.place(new RequestBuilder().page()
      .forItem(item)
      .withRequesterId(requester.getId())
      .withRequestDate(DateTime.now())
      .withStatus(OPEN_NOT_YET_FILLED)
      .withPickupServicePoint(pickupServicePoint)
      .withRequestExpiration(requestExpiration));

    waitAtMost(1, SECONDS)
      .until(scheduledNoticesClient::getAll, hasSize(1));

    DateTime nextRunTimeBeforeProcessing = DateTime.parse(scheduledNoticesClient.getAll()
      .get(0).getString("nextRunTime"));

    scheduledNoticeProcessingClient.runRequestNoticesProcessing();

    final var notices = patronNoticesClient.getAll();

    DateTime nextRunTimeAfterProcessing = DateTime.parse(scheduledNoticesClient.getAll()
      .get(0).getString("nextRunTime"));

    assertThat(notices, hasSize(1));
    assertThat(nextRunTimeBeforeProcessing, is(nextRunTimeAfterProcessing.minusDays(1)));
    assertThat(notices.get(0), getTemplateContextMatcher(templateId, request));

    assertThat(FakePubSub.getPublishedEventsAsList(byLogEventType(NOTICE.value())), hasSize(1));
    assertThatPublishedLogRecordEventsAreValid();
  }

  @Test
  public void beforeHoldExpirationNoticeShouldBeSentAndDeletedWhenIsNotRecurring() {
    JsonObject noticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withHoldShelfExpirationEvent()
      .withBeforeTiming(Period.days(5))
      .sendInRealTime(true)
      .create();
    setupNoticePolicyWithRequestNotice(noticeConfiguration);

    final var requestExpiration = LocalDate.now(getClockManager().getClock()).plusMonths(3);

    IndividualResource request = requestsFixture.place(new RequestBuilder().page()
      .forItem(item)
      .withRequesterId(requester.getId())
      .withRequestDate(DateTime.now())
      .withStatus(OPEN_NOT_YET_FILLED)
      .withPickupServicePoint(pickupServicePoint)
      .withRequestExpiration(requestExpiration));

    CheckInByBarcodeRequestBuilder builder = new CheckInByBarcodeRequestBuilder()
      .forItem(item)
      .withItemBarcode(item.getBarcode())
      .at(pickupServicePoint);
    checkInFixture.checkInByBarcode(builder);

    waitAtMost(1, SECONDS)
      .until(scheduledNoticesClient::getAll, hasSize(1));

    scheduledNoticeProcessingClient.runRequestNoticesProcessing(
      org.joda.time.LocalDate.now(UTC).plusDays(28).toDateTimeAtStartOfDay());

    final var notices = patronNoticesClient.getAll();

    assertThat(notices, hasSize(1));
    assertThat(notices.get(0), getTemplateContextMatcher(templateId, requestsClient.get(request.getId())));

    assertThat(FakePubSub.getPublishedEventsAsList(byLogEventType(NOTICE.value())), hasSize(1));
    assertThatPublishedLogRecordEventsAreValid();

    assertThat(scheduledNoticesClient.getAll(), hasSize(0));
  }

  @Test
  public void scheduledNoticesShouldNotBeSentAfterRequestCancellation() {
    JsonObject noticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withHoldShelfExpirationEvent()
      .withBeforeTiming(Period.minutes(35))
      .recurring(Period.minutes(5))
      .sendInRealTime(true)
      .create();
    setupNoticePolicyWithRequestNotice(noticeConfiguration);

    IndividualResource request = requestsFixture.place(new RequestBuilder()
      .page()
      .forItem(item)
      .withRequesterId(requester.getId())
      .withRequestDate(DateTime.now())
      .withStatus(OPEN_NOT_YET_FILLED)
      .withPickupServicePoint(pickupServicePoint)
      .withNoRequestExpiration());

    CheckInByBarcodeRequestBuilder builder = new CheckInByBarcodeRequestBuilder()
      .forItem(item)
      .withItemBarcode(item.getBarcode())
      .at(pickupServicePoint);
    checkInFixture.checkInByBarcode(builder);

    waitAtMost(1, SECONDS)
      .until(scheduledNoticesClient::getAll, hasSize(1));

    requestsFixture.cancelRequest(request);

    waitAtMost(1, SECONDS)
      .until(scheduledNoticesClient::getAll, empty());
    assertThat(patronNoticesClient.getAll(), hasSize(0));
  }

  @Test
  public void uponAtNoticesShouldBeSentWhenRequestPickupExpired() {
    JsonObject noticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withHoldShelfExpirationEvent()
      .withUponAtTiming()
      .sendInRealTime(true)
      .create();
    setupNoticePolicyWithRequestNotice(noticeConfiguration);

    RequestBuilder requestBuilder = new RequestBuilder()
      .page()
      .forItem(item)
      .withRequesterId(requester.getId())
      .withRequestDate(DateTime.now())
      .withStatus(OPEN_NOT_YET_FILLED)
      .withPickupServicePoint(pickupServicePoint);
    IndividualResource request = requestsFixture.place(requestBuilder);

    checkInFixture.checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(item)
      .withItemBarcode(item.getBarcode())
      .at(pickupServicePoint));

    waitAtMost(1, SECONDS)
      .until(scheduledNoticesClient::getAll, hasSize(1));
    assertThat(patronNoticesClient.getAll(), empty());

    requestsStorageClient.replace(request.getId(), requestBuilder
      .withStatus(CLOSED_PICKUP_EXPIRED));

    scheduledNoticeProcessingClient.runRequestNoticesProcessing(
      org.joda.time.LocalDate.now(UTC).plusDays(35).toDateTimeAtStartOfDay());

    waitAtMost(1, SECONDS)
      .until(scheduledNoticesClient::getAll, empty());
    assertThat(patronNoticesClient.getAll(), hasSize(1));
  }

  private void setupNoticePolicyWithRequestNotice(JsonObject noticeConfiguration) {

    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with request notices")
      .withRequestNotices(singletonList(noticeConfiguration));

    useFallbackPolicies(
      loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicy).getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());
  }

  private Matcher<JsonObject> getTemplateContextMatcher(UUID templateId, IndividualResource request) {
    Map<String, Matcher<String>> templateContextMatchers = new HashMap<>();
    templateContextMatchers.putAll(TemplateContextMatchers.getUserContextMatchers(requester));
    templateContextMatchers.putAll(TemplateContextMatchers.getItemContextMatchers(item, true));
    templateContextMatchers.put("request.servicePointPickup", notNullValue(String.class));
    templateContextMatchers.put("request.requestExpirationDate ",
      isEquivalentTo(getDateTimeProperty(request.getJson(), "requestExpirationDate")));

    return hasEmailNoticeProperties(requester.getId(), templateId, templateContextMatchers);
  }
}
