package api.requests;

import static api.support.TlrFeatureStatus.ENABLED;
import static api.support.builders.RequestBuilder.CLOSED_FILLED;
import static api.support.builders.RequestBuilder.CLOSED_PICKUP_EXPIRED;
import static api.support.builders.RequestBuilder.OPEN_NOT_YET_FILLED;
import static api.support.fixtures.TemplateContextMatchers.getInstanceContextMatchers;
import static api.support.fixtures.TemplateContextMatchers.getItemContextMatchers;
import static api.support.fixtures.TemplateContextMatchers.getRequestContextMatchers;
import static api.support.fixtures.TemplateContextMatchers.getUserContextMatchers;
import static api.support.matchers.PatronNoticeMatcher.hasEmailNoticeProperties;
import static api.support.matchers.RequestMatchers.isOpenAwaitingPickup;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static api.support.matchers.ValidationErrorMatchers.hasCode;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfPublishedEvents;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfScheduledNotices;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfSentNotices;
import static java.time.ZoneOffset.UTC;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.waitAtMost;
import static org.folio.circulation.domain.RequestType.HOLD;
import static org.folio.circulation.domain.RequestType.PAGE;
import static org.folio.circulation.domain.notice.NoticeEventType.REQUEST_EXPIRATION;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE_ERROR;
import static org.folio.circulation.support.ErrorCode.REQUEST_LEVEL_IS_NOT_ALLOWED;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.utils.ClockUtil.getLocalDate;
import static org.folio.circulation.support.utils.ClockUtil.getZonedDateTime;
import static org.folio.circulation.support.utils.DateTimeUtil.atStartOfDay;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.ErrorCode;
import org.folio.circulation.support.http.client.Response;
import org.hamcrest.Matcher;
import org.junit.FixMethodOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.runners.MethodSorters;

import api.support.APITests;
import api.support.TlrFeatureStatus;
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
import api.support.matchers.UUIDMatcher;
import io.vertx.core.json.JsonObject;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class RequestScheduledNoticesProcessingTests extends APITests {
  private static final UUID TEMPLATE_ID = UUID.randomUUID();
  private static final UUID EXPIRATION_TEMPLATE_ID_FROM_NOTICE_POLICY = UUID.randomUUID();
  private static final UUID EXPIRATION_TEMPLATE_ID_FROM_TLR_SETTINGS = UUID.randomUUID();
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

    templateFixture.createDummyNoticeTemplate(TEMPLATE_ID);
  }

  /**
   * method name starting with a for @FixMethodOrder(MethodSorters.NAME_ASCENDING) .
   * FIXME: remove the cause that make this method fail when executed after the others of this class.
   */
  @Test
  void aUponAtRequestExpirationNoticeShouldBeSentAndDeletedWhenRequestExpirationDateHasPassed() {
    JsonObject noticeConfiguration = buildNoticeConfigurationForItemLevelRequests();
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
      FakeModNotify.getFirstSentPatronNotice(), getTemplateContextMatcher(TEMPLATE_ID, request));

    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void uponAtRequestExpirationNoticeShouldNotBeSentWhenRequestExpirationDateHasPassedAndRequestIsNotClosed() {
    JsonObject noticeConfiguration = buildNoticeConfigurationForItemLevelRequests();
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
  void uponAtRequestExpirationNoticeShouldNotBeSentWhenRequestIsClosedFilled() {
    JsonObject noticeConfiguration = buildNoticeConfigurationForItemLevelRequests();
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

    checkOutFixture.checkOutByBarcode(item, requester);

    waitAtMost(1, SECONDS)
      .until(() -> requestsClient.get(request.getId()).getJson().getString("status"),
        equalTo(CLOSED_FILLED));

    scheduledNoticeProcessingClient.runRequestNoticesProcessing(getZonedDateTime().plusMonths(2));

    verifyNumberOfScheduledNotices(0);
    verifyNumberOfSentNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  @Disabled("notice is deleted once the request status is changed to 'Closed - Pickup expired'")
  //TODO fix this test and make it useful again
  void uponAtHoldExpirationNoticeShouldBeSentAndDeletedWhenHoldExpirationDateHasPassed() {
    JsonObject noticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(TEMPLATE_ID)
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
      .withTemplateId(TEMPLATE_ID)
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
      .withTemplateId(TEMPLATE_ID)
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
      .withTemplateId(TEMPLATE_ID)
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
    assertThat(FakeModNotify.getFirstSentPatronNotice(), getTemplateContextMatcher(TEMPLATE_ID, request));

    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void beforeRequestExpirationRecurringNoticeShouldBeSentAndUpdatedWhenFirstThresholdBeforeExpirationHasPassed() {
    JsonObject noticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(TEMPLATE_ID)
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
    assertThat(FakeModNotify.getFirstSentPatronNotice(), getTemplateContextMatcher(TEMPLATE_ID, request));

    verifyNumberOfScheduledNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void beforeHoldExpirationNoticeShouldBeSentAndDeletedWhenIsNotRecurring() {
    JsonObject noticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(TEMPLATE_ID)
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
      getTemplateContextMatcher(TEMPLATE_ID, requestsClient.get(request.getId())));

    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void scheduledNoticesShouldNotBeSentAfterRequestCancellation() {
    JsonObject noticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(TEMPLATE_ID)
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
      .withTemplateId(TEMPLATE_ID)
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

    templateFixture.delete(TEMPLATE_ID);

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
  void scheduledNoticesShouldNotBeSentWhenRequestIdIsNull() {
    prepareNotice();

    JsonObject notice = scheduledNoticesClient.getAll().get(0);
    scheduledNoticesClient.replace(UUID.fromString(notice.getString("id")), notice.put("requestId", null));

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

  @Test
  void titleLevelRequestExpirationNoticeShouldBeSentAndDeletedWithEnabledTlr() {
    reconfigureTlrFeature(TlrFeatureStatus.ENABLED, null, null, TEMPLATE_ID);

    final LocalDate localDate = getLocalDate().minusDays(1);
    final var requestExpiration = LocalDate.of(localDate.getYear(),
      localDate.getMonthValue(), localDate.getDayOfMonth());
    checkOutFixture.checkOutByBarcode(item); // make item unavailable to allow hold request
    IndividualResource request = requestsFixture.place(buildTitleLevelRequest(requestExpiration));

    verifyNumberOfScheduledNotices(1);

    //close request
    IndividualResource requestInStorage = requestsStorageClient.get(request);
    requestsStorageClient.replace(request.getId(),
      requestInStorage.getJson().put("status", "Closed - Unfilled"));

    scheduledNoticeProcessingClient.runRequestNoticesProcessing();

    verifyNumberOfSentNotices(1);
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @ParameterizedTest
  @MethodSource("templatesId")
  void titleLevelRequestExpirationNoticeShouldNotBeCreatedWithDisabledTlr(UUID expirationTemplateId) {
    reconfigureTlrFeature(TlrFeatureStatus.DISABLED, null, null, expirationTemplateId);

    final LocalDate localDate = getLocalDate().minusDays(1);
    final var requestExpiration = LocalDate.of(localDate.getYear(),
      localDate.getMonthValue(), localDate.getDayOfMonth());

    Response response = requestsFixture.attemptPlace(buildTitleLevelRequest(requestExpiration));

    assertThat(response.getStatusCode(), is(422));
    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Request level must be one of the following: \"Item\""),
      hasCode(REQUEST_LEVEL_IS_NOT_ALLOWED))));
    verifyNumberOfScheduledNotices(0);
  }

  @Test
  void titleLevelRequestExpirationNoticeShouldNotBeCreatedIfEnabledTlrButNoTemplateId() {
    reconfigureTlrFeature(TlrFeatureStatus.ENABLED, null, null, null);

    final LocalDate localDate = getLocalDate().minusDays(1);
    final var requestExpiration = LocalDate.of(localDate.getYear(),
      localDate.getMonthValue(), localDate.getDayOfMonth());
    requestsFixture.place(buildTitleLevelRequest(requestExpiration).page());

    verifyNumberOfScheduledNotices(0);
  }

  @ParameterizedTest
  @EnumSource(TlrFeatureStatus.class)
  void itemLevelRequestExpirationNoticeShouldBeCreatedAndSentRegardlessTlrSettings(
    TlrFeatureStatus tlrFeatureStatus) {

    reconfigureTlrFeature(tlrFeatureStatus, null, null, null);
    JsonObject noticeConfiguration = buildNoticeConfigurationForItemLevelRequests();
    setupNoticePolicyWithRequestNotice(noticeConfiguration);

    final LocalDate localDate = getLocalDate().minusDays(1);
    final var requestExpiration = LocalDate.of(localDate.getYear(),
      localDate.getMonthValue(), localDate.getDayOfMonth());

    IndividualResource request = requestsFixture.place(buildItemLevelRequest(requestExpiration));

    verifyNumberOfScheduledNotices(1);

    //close request
    IndividualResource requestInStorage = requestsStorageClient.get(request);

    requestsStorageClient.replace(request.getId(),
      requestInStorage.getJson().put("status", "Closed - Unfilled"));

    scheduledNoticeProcessingClient.runRequestNoticesProcessing();

    verifyNumberOfSentNotices(1);
    assertThat(
      FakeModNotify.getFirstSentPatronNotice(), getTemplateContextMatcher(TEMPLATE_ID, request));

    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void itemLevelRequestExpirationNoticeAndTitleLevelRequestExpirationShouldBeCreatedAndSent() {
    reconfigureTlrFeature(TlrFeatureStatus.ENABLED, null, null, TEMPLATE_ID);
    JsonObject noticeConfiguration = buildNoticeConfigurationForItemLevelRequests();
    setupNoticePolicyWithRequestNotice(noticeConfiguration);

    final LocalDate localDate = getLocalDate().minusDays(1);
    final var requestExpiration = LocalDate.of(localDate.getYear(),
      localDate.getMonthValue(), localDate.getDayOfMonth());

    IndividualResource itemLevelRequest = requestsFixture.place(
      buildItemLevelRequest(requestExpiration));
    IndividualResource titleLevelRequest = requestsFixture.place(
      buildTitleLevelRequest(requestExpiration));
    verifyNumberOfScheduledNotices(2);

    //close requests
    IndividualResource itemLevelRequestInStorage = requestsStorageClient.get(itemLevelRequest);
    IndividualResource titleLevelRequestInStorage = requestsStorageClient.get(titleLevelRequest);

    requestsStorageClient.replace(itemLevelRequest.getId(),
      itemLevelRequestInStorage.getJson().put("status", "Closed - Unfilled"));
    requestsStorageClient.replace(titleLevelRequest.getId(),
      titleLevelRequestInStorage.getJson().put("status", "Closed - Unfilled"));

    scheduledNoticeProcessingClient.runRequestNoticesProcessing();

    verifyNumberOfSentNotices(2);
    verifyNumberOfScheduledNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 2);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "true,  true",
    "false, true",
    "true,  false",
    "false, false"
  })
  void expirationNoticeForTitleLevelRequestWithItemIdIsScheduledAccordingToNoticePolicy(
    boolean isNoticeEnabledInTlrSettings, boolean isNoticeEnabledInNoticePolicy) {

    setUpPatronNotices(isNoticeEnabledInTlrSettings, isNoticeEnabledInNoticePolicy);

    UserResource requester = usersFixture.james();
    LocalDate requestExpirationDate = getLocalDate().plusDays(1);
    IndividualResource request = requestsFixture.placeTitleLevelRequest(
      PAGE, item.getInstanceId(), requester, requestExpirationDate);

    assertThat(request.getJson().getString("itemId"), UUIDMatcher.is(item.getId()));
    verifyNumberOfScheduledNotices(isNoticeEnabledInNoticePolicy ? 1 : 0);

    IndividualResource requestInStorage = requestsStorageClient.get(request);
    requestsStorageClient.replace(request.getId(),
      requestInStorage.getJson().put("status", "Closed - Unfilled"));

    scheduledNoticeProcessingClient.runRequestNoticesProcessing(
      requestExpirationDate.atStartOfDay(UTC).plusDays(1));

    // if request has itemId, notices are sent according to notice policy, regardless of TLR settings
    if (isNoticeEnabledInNoticePolicy) {
      verifyNumberOfScheduledNotices(0);
      verifyNumberOfPublishedEvents(NOTICE, 1);
      verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
      JsonObject sentNotice = verifyNumberOfSentNotices(1).get(0);

      Map<String, Matcher<String>> matchers = new HashMap<>();
      matchers.putAll(getUserContextMatchers(requester));
      matchers.putAll(getRequestContextMatchers(request));
      matchers.putAll(getItemContextMatchers(item, true));

      assertThat(sentNotice, hasEmailNoticeProperties(requester.getId(),
        EXPIRATION_TEMPLATE_ID_FROM_NOTICE_POLICY, matchers));
    } else {
      verifyNumberOfSentNotices(0);
      verifyNumberOfScheduledNotices(0);
      verifyNumberOfPublishedEvents(NOTICE, 0);
      verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    }
  }

  @ParameterizedTest
  @CsvSource(value = {
    "true,  true",
    "false, true",
    "true,  false",
    "false, false"
  })
  void expirationNoticeForTitleLevelRequestWithoutItemIdIsScheduledAccordingToTlrSettings(
    boolean isNoticeEnabledInTlrSettings, boolean isNoticeEnabledInNoticePolicy) {

    setUpPatronNotices(isNoticeEnabledInTlrSettings, isNoticeEnabledInNoticePolicy);

    checkOutFixture.checkOutByBarcode(item, usersFixture.rebecca()); // make item unavailable
    UserResource requester = usersFixture.james();
    LocalDate requestExpirationDate = getLocalDate().plusDays(1);
    IndividualResource request = requestsFixture.placeTitleLevelRequest(
      HOLD, item.getInstanceId(), requester, requestExpirationDate);

    assertThat(request.getJson().getString("itemId"), nullValue());
    verifyNumberOfScheduledNotices(isNoticeEnabledInTlrSettings ? 1 : 0);

    IndividualResource requestInStorage = requestsStorageClient.get(request);
    requestsStorageClient.replace(request.getId(),
      requestInStorage.getJson().put("status", "Closed - Unfilled"));

    scheduledNoticeProcessingClient.runRequestNoticesProcessing(
      requestExpirationDate.atStartOfDay(UTC).plusDays(1));

    // if request has no itemId, notices are sent according to TLR settings, regardless of notice policy
    if (isNoticeEnabledInTlrSettings) {
      verifyNumberOfScheduledNotices(0);
      verifyNumberOfPublishedEvents(NOTICE, 1);
      verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
      JsonObject sentNotice = verifyNumberOfSentNotices(1).get(0);

      JsonObject instance = instancesClient.getById(item.getInstanceId()).getJson();
      Map<String, Matcher<String>> matchers = new HashMap<>();
      matchers.putAll(getUserContextMatchers(requester));
      matchers.putAll(getRequestContextMatchers(request));
      matchers.putAll(getInstanceContextMatchers(instance));

      assertThat(sentNotice, hasEmailNoticeProperties(requester.getId(),
        EXPIRATION_TEMPLATE_ID_FROM_TLR_SETTINGS, matchers));
    } else {
      verifyNumberOfSentNotices(0);
      verifyNumberOfPublishedEvents(NOTICE, 0);
      verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    }
  }

  private void setUpPatronNotices(boolean isNoticeEnabledInTlrSettings,
    boolean isNoticeEnabledInNoticePolicy) {

    // set up TLR settings
    if (isNoticeEnabledInTlrSettings) {
      templateFixture.createDummyNoticeTemplate(EXPIRATION_TEMPLATE_ID_FROM_TLR_SETTINGS);
      reconfigureTlrFeature(ENABLED, null, null, EXPIRATION_TEMPLATE_ID_FROM_TLR_SETTINGS);
    } else {
      reconfigureTlrFeature(ENABLED, null, null, null);
    }

    // set up patron notice policy
    if (isNoticeEnabledInNoticePolicy) {
      templateFixture.createDummyNoticeTemplate(EXPIRATION_TEMPLATE_ID_FROM_NOTICE_POLICY);
      use(new NoticePolicyBuilder()
        .withName("Test patron notice policy")
        .withRequestNotices(List.of(
          new NoticeConfigurationBuilder()
            .withTemplateId(EXPIRATION_TEMPLATE_ID_FROM_NOTICE_POLICY)
            .withEventType(REQUEST_EXPIRATION.getRepresentation())
            .create()
        )));
    }
  }

  private static Stream<UUID> templatesId() {
    return Stream.of(TEMPLATE_ID, null);
  }

  private JsonObject buildNoticeConfigurationForItemLevelRequests() {
    return new NoticeConfigurationBuilder()
      .withTemplateId(TEMPLATE_ID)
      .withRequestExpirationEvent()
      .withUponAtTiming()
      .sendInRealTime(true)
      .create();
  }

  private RequestBuilder buildItemLevelRequest(LocalDate requestExpiration) {
    return new RequestBuilder()
      .forItem(item)
      .page()
      .itemRequestLevel()
      .withInstanceId(item.getInstanceId())
      .withRequesterId(requester.getId())
      .withRequestDate(getZonedDateTime())
      .withStatus(OPEN_NOT_YET_FILLED)
      .withPickupServicePoint(pickupServicePoint)
      .withRequestExpiration(requestExpiration);
  }

  private RequestBuilder buildTitleLevelRequest(LocalDate requestExpiration) {
    return new RequestBuilder()
      .hold()
      .titleRequestLevel()
      .withNoItemId()
      .withNoHoldingsRecordId()
      .withInstanceId(item.getInstanceId())
      .withRequesterId(usersFixture.charlotte().getId())
      .withRequestDate(getZonedDateTime())
      .withStatus(OPEN_NOT_YET_FILLED)
      .withPickupServicePoint(pickupServicePoint)
      .withRequestExpiration(requestExpiration);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "In process",
    "In process (non-requestable)",
    "Intellectual item",
    "Long missing",
    "Missing",
    "Restricted",
    "Unavailable",
    "Unknown",
    "Withdrawn"
  })
  void uponAtHoldExpirationNoticeShouldBeDeletedWithoutSendingWhenItemIsMarkedAs(String itemStatus) {
    setupNoticePolicyWithRequestNotice(
      new NoticeConfigurationBuilder()
        .withTemplateId(TEMPLATE_ID)
        .withHoldShelfExpirationEvent()
        .withUponAtTiming()
        .sendInRealTime(true)
        .create());

    IndividualResource request = requestsFixture.place(new RequestBuilder().page()
      .forItem(item)
      .withRequesterId(requester.getId())
      .withRequestDate(getZonedDateTime())
      .withStatus(OPEN_NOT_YET_FILLED)
      .withPickupServicePoint(pickupServicePoint));

    checkInFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(item)
        .withItemBarcode(item.getBarcode())
        .at(pickupServicePoint));

    assertThat(requestsClient.getById(request.getId()).getJson(), isOpenAwaitingPickup());
    verifyNumberOfScheduledNotices(1);

    markItemAs(itemStatus, item.getId(), request.getId());

    scheduledNoticeProcessingClient.runRequestNoticesProcessing(
      atStartOfDay(getLocalDate().plusDays(31), UTC));

    verifyNumberOfScheduledNotices(0);
    verifyNumberOfSentNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  private IndividualResource prepareNotice() {
    setupNoticePolicyWithRequestNotice(
      new NoticeConfigurationBuilder()
        .withTemplateId(TEMPLATE_ID)
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

  // Update item and request to imitate mod-inventory's behavior upon marking an item
  // as Withdrawn, Missing, Restricted, etc.
  private void markItemAs(String itemStatus, UUID itemId, UUID requestId) {
    JsonObject item = itemsClient.get(itemId).getJson();
    item.getJsonObject("status").put("name", itemStatus);
    itemsClient.replace(itemId, item);

    IndividualResource request = requestsStorageClient.get(requestId);
    requestsStorageClient.replace(requestId,
      request.getJson().put("status", "Open - Not yet filled"));
  }

}
