package api.requests;

import static api.support.builders.RequestBuilder.CLOSED_PICKUP_EXPIRED;
import static api.support.builders.RequestBuilder.OPEN_NOT_YET_FILLED;
import static api.support.matchers.PatronNoticeMatcher.hasEmailNoticeProperties;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfPublishedEvents;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfScheduledNotices;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfSentNotices;
import static java.time.ZoneOffset.UTC;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.waitAtMost;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE_ERROR;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.utils.ClockUtil.getLocalDate;
import static org.folio.circulation.support.utils.ClockUtil.getZonedDateTime;
import static org.folio.circulation.support.utils.DateTimeUtil.atStartOfDay;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.folio.circulation.domain.policy.Period;
import org.hamcrest.Matcher;
import org.junit.FixMethodOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
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
class RequestScheduledNoticesProcessingTests extends APITests {
  private final UUID templateId = UUID.randomUUID();
  private ItemResource item;
  private UserResource requester;
  private IndividualResource pickupServicePoint;

  @BeforeEach
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
  void aUponAtRequestExpirationNoticeShouldBeSentAndDeletedWhenRequestExpirationDateHasPassed() {
    JsonObject noticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withRequestExpirationEvent()
      .withUponAtTiming()
      .sendInRealTime(true)
      .create();
    setupNoticePolicyWithRequestNotice(noticeConfiguration);

    final LocalDate localDate = getLocalDate().minusDays(1);
    final var requestExpiration = LocalDate.of(localDate.getYear(),
      localDate.getMonth(), localDate.getDayOfMonth());

    IndividualResource request = requestsFixture.place(new RequestBuilder().page()
      .forItem(item)
      .withRequesterId(requester.getId())
      .withRequestDate(getZonedDateTime())
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
  void uponAtRequestExpirationNoticeShouldNotBeSentWhenRequestExpirationDateHasPassedAndRequestIsNotClosed() {
    JsonObject noticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withRequestExpirationEvent()
      .withUponAtTiming()
      .sendInRealTime(true)
      .create();
    setupNoticePolicyWithRequestNotice(noticeConfiguration);

    final LocalDate localDate = getLocalDate().minusDays(1);
    final var requestExpiration = LocalDate.of(localDate.getYear(),
      localDate.getMonth(), localDate.getDayOfMonth());

    requestsFixture.place(new RequestBuilder().page()
      .forItem(item)
      .withRequesterId(requester.getId())
      .withRequestDate(getZonedDateTime())
      .withStatus(OPEN_NOT_YET_FILLED)
      .withPickupServicePoint(pickupServicePoint)
      .withRequestExpiration(requestExpiration));

    verifyNumberOfScheduledNotices(1);

    scheduledNoticeProcessingClient.runRequestNoticesProcessing(getZonedDateTime().plusMonths(2));

    verifyNumberOfScheduledNotices(1);
    verifyNumberOfSentNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  @Disabled("notice is deleted once the request status is changed to 'Closed - Pickup expired'")
  //TODO fix this test and make it useful again
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
      .withRequestDate(getZonedDateTime())
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
      atStartOfDay(getLocalDate().plusDays(31), UTC));

    verifyNumberOfScheduledNotices(0);
    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void uponAtHoldExpirationNoticeShouldNotBeSentWhenHoldExpirationDateHasPassedAndRequestIsNotClosed() {
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
      .withRequestDate(getZonedDateTime())
      .withStatus(OPEN_NOT_YET_FILLED)
      .withPickupServicePoint(pickupServicePoint));

    CheckInByBarcodeRequestBuilder builder = new CheckInByBarcodeRequestBuilder()
      .forItem(item)
      .withItemBarcode(item.getBarcode())
      .at(pickupServicePoint);
    checkInFixture.checkInByBarcode(builder);

    verifyNumberOfScheduledNotices(1);

    scheduledNoticeProcessingClient.runRequestNoticesProcessing(
      atStartOfDay(getLocalDate().plusDays(31), UTC));

    verifyNumberOfScheduledNotices(1);
    verifyNumberOfSentNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void uponAtHoldExpirationNoticeShouldNotBeSentWhenHoldExpirationDateHasPassedAndItemCheckedOut() {
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
      .withRequestDate(getZonedDateTime())
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
      atStartOfDay(getLocalDate().plusDays(100), UTC));

    verifyNumberOfScheduledNotices(0);
    verifyNumberOfSentNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void beforeRequestExpirationNoticeShouldBeSentAndDeletedWhenIsNotRecurring() {
    JsonObject noticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withRequestExpirationEvent()
      .withBeforeTiming(Period.days(5))
      .sendInRealTime(true)
      .create();
    setupNoticePolicyWithRequestNotice(noticeConfiguration);

    final LocalDate localDate = getLocalDate()
      .plusDays(4);
    final var requestExpiration = LocalDate.of(localDate.getYear(),
      localDate.getMonthValue(), localDate.getDayOfMonth());

    IndividualResource request = requestsFixture.place(new RequestBuilder().page()
      .forItem(item)
      .withRequesterId(requester.getId())
      .withRequestDate(getZonedDateTime())
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
  void beforeRequestExpirationRecurringNoticeShouldBeSentAndUpdatedWhenFirstThresholdBeforeExpirationHasPassed() {
    JsonObject noticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withRequestExpirationEvent()
      .withBeforeTiming(Period.days(3))
      .recurring(Period.days(1))
      .sendInRealTime(true)
      .create();
    setupNoticePolicyWithRequestNotice(noticeConfiguration);

    final LocalDate localDate = getLocalDate().plusDays(2);
    final var requestExpiration = LocalDate.of(localDate.getYear(),
      localDate.getMonthValue(), localDate.getDayOfMonth());

    IndividualResource request = requestsFixture.place(new RequestBuilder().page()
      .forItem(item)
      .withRequesterId(requester.getId())
      .withRequestDate(getZonedDateTime())
      .withStatus(OPEN_NOT_YET_FILLED)
      .withPickupServicePoint(pickupServicePoint)
      .withRequestExpiration(requestExpiration));

    verifyNumberOfScheduledNotices(1);

    ZonedDateTime nextRunTimeBeforeProcessing = ZonedDateTime.parse(scheduledNoticesClient.getAll()
      .get(0).getString("nextRunTime"));

    scheduledNoticeProcessingClient.runRequestNoticesProcessing();

    ZonedDateTime nextRunTimeAfterProcessing = ZonedDateTime.parse(scheduledNoticesClient.getAll()
      .get(0).getString("nextRunTime"));

    assertThat(nextRunTimeBeforeProcessing, is(nextRunTimeAfterProcessing.minusDays(1)));

    verifyNumberOfSentNotices(1);
    assertThat(FakeModNotify.getFirstSentPatronNotice(), getTemplateContextMatcher(templateId, request));

    verifyNumberOfScheduledNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void beforeHoldExpirationNoticeShouldBeSentAndDeletedWhenIsNotRecurring() {
    JsonObject noticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withHoldShelfExpirationEvent()
      .withBeforeTiming(Period.days(5))
      .sendInRealTime(true)
      .create();
    setupNoticePolicyWithRequestNotice(noticeConfiguration);

    final LocalDate localDate = getLocalDate().plusMonths(3);
    final var requestExpiration = LocalDate.of(localDate.getYear(),
      localDate.getMonthValue(), localDate.getDayOfMonth());

    IndividualResource request = requestsFixture.place(new RequestBuilder().page()
      .forItem(item)
      .withRequesterId(requester.getId())
      .withRequestDate(getZonedDateTime())
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
    atStartOfDay(getLocalDate().plusDays(28), UTC));

    verifyNumberOfSentNotices(1);
    assertThat(FakeModNotify.getFirstSentPatronNotice(),
      getTemplateContextMatcher(templateId, requestsClient.get(request.getId())));

    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void scheduledNoticesShouldNotBeSentAfterRequestCancellation() {
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
      .withRequestDate(getZonedDateTime())
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
  void uponAtNoticesShouldBeSentWhenRequestPickupExpired() {
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
      .withRequestDate(getZonedDateTime())
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
      atStartOfDay(getLocalDate().plusDays(35), UTC));

    verifyNumberOfScheduledNotices(0);
    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void scheduledNoticesShouldNotBeSentWhenTemplateWasNotFound() {
    prepareNotice();

    templateFixture.delete(templateId);

    scheduledNoticeProcessingClient.runRequestNoticesProcessing(getZonedDateTime().plusMonths(2));

    verifyNumberOfSentNotices(0);
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @Test
  void scheduledNoticesShouldNotBeSentWhenRequestWasNotFound() {
    IndividualResource request = prepareNotice();

    requestsStorageClient.delete(request);

    scheduledNoticeProcessingClient.runRequestNoticesProcessing(getZonedDateTime().plusMonths(2));

    verifyNumberOfSentNotices(0);
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @Test
  void scheduledNoticesShouldNotBeSentWhenRequestIdWasNull() {
    prepareNotice();

    JsonObject entries = scheduledNoticesClient.getAll().get(0);
    scheduledNoticesClient.replace(UUID.fromString(entries.getString("id")), entries.put("requestId", null));

    scheduledNoticeProcessingClient.runRequestNoticesProcessing(getZonedDateTime().plusMonths(2));

    verifyNumberOfSentNotices(0);
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @Test
  void scheduledNoticesShouldNotBeSentWhenUserWasNotFound() {
    prepareNotice();

    usersFixture.remove(requester);

    scheduledNoticeProcessingClient.runRequestNoticesProcessing(getZonedDateTime().plusMonths(2));

    verifyNumberOfSentNotices(0);
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @Test
  void scheduledNoticesShouldNotBeSentWhenItemWasNotFound() {
    prepareNotice();

    itemsClient.delete(item);

    scheduledNoticeProcessingClient.runRequestNoticesProcessing(getZonedDateTime().plusMonths(2));

    verifyNumberOfSentNotices(0);
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @Test
  void scheduledNoticesShouldNotBeSentWhenPatronNoticeRequestFails() {
    prepareNotice();

    FakeModNotify.setFailPatronNoticesWithBadRequest(true);

    scheduledNoticeProcessingClient.runRequestNoticesProcessing(getZonedDateTime().plusMonths(2));

    verifyNumberOfSentNotices(0);
    verifyNumberOfScheduledNotices(0);
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

    final LocalDate localDate = getLocalDate();
    final var requestExpiration = LocalDate.of(localDate.getYear(),
      localDate.getMonth(), localDate.getDayOfMonth());

    IndividualResource request = requestsFixture.place(
      new RequestBuilder()
        .page()
        .forItem(item)
        .withRequesterId(requester.getId())
        .withRequestDate(getZonedDateTime())
        .withRequestExpiration(requestExpiration)
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
