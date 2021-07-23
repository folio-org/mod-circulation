package api.requests;

import static api.support.builders.RequestBuilder.CLOSED_PICKUP_EXPIRED;
import static api.support.builders.RequestBuilder.OPEN_NOT_YET_FILLED;
import static api.support.matchers.PatronNoticeMatcher.hasEmailNoticeProperties;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfPublishedEvents;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfScheduledNotices;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfSentNotices;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.waitAtMost;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE_ERROR;
import static org.folio.circulation.support.ClockManager.getClockManager;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
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
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import api.support.APITests;
import api.support.builders.CheckInByBarcodeRequestBuilder;
import api.support.builders.HoldingBuilder;
import api.support.builders.ItemBuilder;
import api.support.builders.NoticeConfigurationBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.builders.RequestBuilder;
import api.support.fakes.FakeModNotify;
import api.support.fixtures.ItemExamples;
import api.support.fixtures.TemplateContextMatchers;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import api.support.http.UserResource;
import io.vertx.core.json.JsonObject;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class RequestScheduledNoticesProcessingTests extends APITests {
  private final UUID templateId = UUID.randomUUID();
  private ItemResource item;
  private UserResource requester;
  private IndividualResource pickupServicePoint;

  @Before
  public void beforeEach() {
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

    templateFixture.createDummyNoticeTemplate(templateId);
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

    verifyNumberOfScheduledNotices(1);

    //close request
    IndividualResource requestInStorage = requestsStorageClient.get(request);

    requestsStorageClient.replace(request.getId(),
      requestInStorage.getJson().put("status", "Closed - Unfilled"));

    scheduledNoticeProcessingClient.runRequestNoticesProcessing();

    verifyNumberOfSentNotices(1);
    assertThat(
      FakeModNotify.getFirstSentPatronNotice(), getTemplateContextMatcher(templateId, request));

    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
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

    verifyNumberOfScheduledNotices(1);

    scheduledNoticeProcessingClient.runRequestNoticesProcessing(DateTime.now().plusMonths(2));

    verifyNumberOfScheduledNotices(1);
    verifyNumberOfSentNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  @Ignore("notice is deleted once the request status is changed to 'Closed - Pickup expired'")
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

    verifyNumberOfScheduledNotices(1);

    //close request
    requestsClient.replace(request.getId(),
      request.getJson().put("status", "Closed - Pickup expired"));

    scheduledNoticeProcessingClient.runRequestNoticesProcessing(
      org.joda.time.LocalDate.now(UTC).plusDays(31).toDateTimeAtStartOfDay());

    verifyNumberOfScheduledNotices(0);
    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
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

    verifyNumberOfScheduledNotices(1);

    scheduledNoticeProcessingClient.runRequestNoticesProcessing(
      org.joda.time.LocalDate.now(UTC).plusDays(31).toDateTimeAtStartOfDay());

    verifyNumberOfScheduledNotices(1);
    verifyNumberOfSentNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
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

    verifyNumberOfScheduledNotices(1);

    assertThat(FakeModNotify.getSentPatronNotices(), empty());

    checkOutFixture.checkOutByBarcode(item, requester);

    waitAtMost(1, SECONDS)
      .until(() -> requestsClient.get(request.getId()).getJson().getString("status"),
        equalTo("Closed - Filled"));

    scheduledNoticeProcessingClient.runRequestNoticesProcessing(
      org.joda.time.LocalDate.now(UTC).plusDays(100).toDateTimeAtStartOfDay());

    verifyNumberOfScheduledNotices(0);
    verifyNumberOfSentNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
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

    verifyNumberOfScheduledNotices(1);

    scheduledNoticeProcessingClient.runRequestNoticesProcessing();

    verifyNumberOfSentNotices(1);
    assertThat(FakeModNotify.getFirstSentPatronNotice(), getTemplateContextMatcher(templateId, request));

    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  public void beforeRequestExpirationRecurringNoticeShouldBeSentAndUpdatedWhenFirstThresholdBeforeExpirationHasPassed() {
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

    verifyNumberOfScheduledNotices(1);

    DateTime nextRunTimeBeforeProcessing = DateTime.parse(scheduledNoticesClient.getAll()
      .get(0).getString("nextRunTime"));

    scheduledNoticeProcessingClient.runRequestNoticesProcessing();

    DateTime nextRunTimeAfterProcessing = DateTime.parse(scheduledNoticesClient.getAll()
      .get(0).getString("nextRunTime"));

    assertThat(nextRunTimeBeforeProcessing, is(nextRunTimeAfterProcessing.minusDays(1)));

    verifyNumberOfSentNotices(1);
    assertThat(FakeModNotify.getFirstSentPatronNotice(), getTemplateContextMatcher(templateId, request));

    verifyNumberOfScheduledNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
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

   verifyNumberOfScheduledNotices(1);

    scheduledNoticeProcessingClient.runRequestNoticesProcessing(
      org.joda.time.LocalDate.now(UTC).plusDays(28).toDateTimeAtStartOfDay());

    verifyNumberOfSentNotices(1);
    assertThat(FakeModNotify.getFirstSentPatronNotice(),
      getTemplateContextMatcher(templateId, requestsClient.get(request.getId())));

    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
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

    verifyNumberOfScheduledNotices(1);

    requestsFixture.cancelRequest(request);

    verifyNumberOfScheduledNotices(0);
    verifyNumberOfSentNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
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

    verifyNumberOfScheduledNotices(1);

    requestsStorageClient.replace(request.getId(), requestBuilder
      .withStatus(CLOSED_PICKUP_EXPIRED));

    scheduledNoticeProcessingClient.runRequestNoticesProcessing(
      org.joda.time.LocalDate.now(UTC).plusDays(35).toDateTimeAtStartOfDay());

    verifyNumberOfScheduledNotices(0);
    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  public void scheduledNoticesShouldNotBeSentWhenTemplateWasNotFound() {
    prepareNotice();

    templateFixture.delete(templateId);

    scheduledNoticeProcessingClient.runRequestNoticesProcessing(DateTime.now().plusMonths(2));

    verifyNumberOfSentNotices(0);
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @Test
  public void scheduledNoticesShouldNotBeSentWhenRequestWasNotFound() {
    IndividualResource request = prepareNotice();

    requestsStorageClient.delete(request);

    scheduledNoticeProcessingClient.runRequestNoticesProcessing(DateTime.now().plusMonths(2));

    verifyNumberOfSentNotices(0);
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @Test
  public void scheduledNoticesShouldNotBeSentWhenUserWasNotFound() {
    prepareNotice();

    usersFixture.remove(requester);

    scheduledNoticeProcessingClient.runRequestNoticesProcessing(DateTime.now().plusMonths(2));

    verifyNumberOfSentNotices(0);
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @Test
  public void scheduledNoticesShouldNotBeSentWhenItemWasNotFound() {
    prepareNotice();

    itemsClient.delete(item);

    scheduledNoticeProcessingClient.runRequestNoticesProcessing(DateTime.now().plusMonths(2));

    verifyNumberOfSentNotices(0);
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @Test
  public void scheduledNoticesShouldNotBeSentOrDeletedWhenPatronNoticeRequestFails() {
    prepareNotice();

    FakeModNotify.setFailPatronNoticesWithBadRequest(true);

    scheduledNoticeProcessingClient.runRequestNoticesProcessing(DateTime.now().plusMonths(2));

    verifyNumberOfSentNotices(0);
    verifyNumberOfScheduledNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  private IndividualResource prepareNotice() {
    setupNoticePolicyWithRequestNotice(
      new NoticeConfigurationBuilder()
        .withTemplateId(templateId)
        .withRequestExpirationEvent()
        .withAfterTiming(Period.hours(1))
        .sendInRealTime(true)
        .create()
    );

    IndividualResource request = requestsFixture.place(
      new RequestBuilder()
        .page()
        .forItem(item)
        .withRequesterId(requester.getId())
        .withRequestDate(DateTime.now())
        .withRequestExpiration(LocalDate.now())
        .withStatus(OPEN_NOT_YET_FILLED)
        .withPickupServicePoint(pickupServicePoint)
    );

    verifyNumberOfScheduledNotices(1);

    return request;
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
