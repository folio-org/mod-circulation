package api.requests;

import static api.support.builders.RequestBuilder.OPEN_NOT_YET_FILLED;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static java.time.ZoneOffset.UTC;
import static org.folio.circulation.domain.representations.RequestProperties.HOLD_SHELF_EXPIRATION_DATE;
import static org.folio.circulation.support.ClockManager.getClockManager;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.awaitility.Awaitility;
import org.folio.circulation.domain.policy.Period;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.CheckInByBarcodeRequestBuilder;
import api.support.builders.HoldingBuilder;
import api.support.builders.ItemBuilder;
import api.support.builders.NoticeConfigurationBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.builders.RequestBuilder;
import api.support.fixtures.ItemExamples;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import io.vertx.core.json.JsonObject;

public class RequestScheduledNoticesTests extends APITests {
  private final UUID templateId = UUID.randomUUID();
  private ItemResource item;
  private IndividualResource requester;
  private IndividualResource pickupServicePoint;

  @Before
  public void beforeEach() {
    ItemBuilder itemBuilder = ItemExamples.basedUponSmallAngryPlanet(
      materialTypesFixture.book().getId(), loanTypesFixture.canCirculate().getId());
    HoldingBuilder holdingBuilder = itemsFixture.applyCallNumberHoldings(
      "CN",
      "Prefix",
      "Suffix",
      Collections.singletonList("CopyNumbers"));

    item = itemsFixture.basedUponSmallAngryPlanet(itemBuilder, holdingBuilder);
    requester = usersFixture.steve();
    pickupServicePoint = servicePointsFixture.cd1();

  }

  @Test
  public void requestExpirationUponAtNoticeShouldBeScheduledWhenCreatedRequestIsSetToExpire() {
    JsonObject requestNotice = new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withRequestExpirationEvent()
      .withUponAtTiming()
      .sendInRealTime(true)
      .create();

    NoticePolicyBuilder noticePolicyBuilder = new NoticePolicyBuilder()
      .withName("request policy")
      .withRequestNotices(Collections.singletonList(requestNotice));

    useDefaultRollingPoliciesAndOnlyAllowPageRequests(noticePolicyBuilder);

    final var requestExpiration = java.time.LocalDate.now(getClockManager().getClock()).plusMonths(3);

    IndividualResource request = requestsFixture.place(new RequestBuilder().page()
      .forItem(item)
      .withRequesterId(requester.getId())
      .withRequestDate(DateTime.now())
      .withStatus(OPEN_NOT_YET_FILLED)
      .withPickupServicePoint(pickupServicePoint)
      .withRequestExpiration(requestExpiration));

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(scheduledNoticesClient::getAll, hasSize(1));

    List<JsonObject> scheduledNotices = scheduledNoticesClient.getAll();
    assertThat(scheduledNotices.size(), is(1));

    JsonObject scheduledNotice = scheduledNotices.get(0);
    JsonObject noticeConfig = scheduledNotice.getJsonObject("noticeConfig");

    assertThat(scheduledNotice.getString("requestId"), is(request.getId().toString()));
    assertThat(scheduledNotice.getString("triggeringEvent"), is("Request expiration"));
    assertThat(scheduledNotice.getString("nextRunTime"),
      isEquivalentTo(ZonedDateTime.of(requestExpiration.atTime(23, 59, 59), UTC)));
    assertThat(noticeConfig.getString("timing"), is("Upon At"));
    assertThat(noticeConfig.getString("templateId"), is(templateId.toString()));
    assertThat(noticeConfig.getString("format"), is("Email"));
    assertThat(noticeConfig.getBoolean("sendInRealTime"), is(true));
  }

  @Test
  public void requestExpirationUponAtNoticeShouldNotBeScheduledWhenCreatedRequestIsNotSetToExpire() {
    JsonObject requestNotice = new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withRequestExpirationEvent()
      .withUponAtTiming()
      .sendInRealTime(true)
      .create();

    NoticePolicyBuilder noticePolicyBuilder = new NoticePolicyBuilder()
      .withName("request policy")
      .withRequestNotices(Collections.singletonList(requestNotice));

    useDefaultRollingPoliciesAndOnlyAllowPageRequests(noticePolicyBuilder);

    requestsFixture.place(new RequestBuilder().page()
      .forItem(item)
      .withRequesterId(requester.getId())
      .withRequestDate(DateTime.now())
      .withStatus(OPEN_NOT_YET_FILLED)
      .withPickupServicePoint(pickupServicePoint));

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(scheduledNoticesClient::getAll, hasSize(0));
  }

  @Test
  public void requestExpirationNoticeShouldNotBeScheduledWhenCreatedRequestDoesNotExpire()
    throws InterruptedException {

    JsonObject requestNotice = new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withRequestExpirationEvent()
      .withUponAtTiming()
      .sendInRealTime(true)
      .create();

    NoticePolicyBuilder noticePolicyBuilder = new NoticePolicyBuilder()
      .withName("request policy")
      .withRequestNotices(Collections.singletonList(requestNotice));

    useDefaultRollingPoliciesAndOnlyAllowPageRequests(noticePolicyBuilder);

    requestsFixture.place(new RequestBuilder().page()
      .forItem(item)
      .withRequesterId(requester.getId())
      .withRequestDate(DateTime.now())
      .withStatus(OPEN_NOT_YET_FILLED)
      .withPickupServicePoint(pickupServicePoint)
      .withNoRequestExpiration());

    TimeUnit.SECONDS.sleep(1);

    List<JsonObject> scheduledNotices = scheduledNoticesClient.getAll();
    assertThat(scheduledNotices, hasSize(0));
  }

  @Test
  public void requestExpirationBeforeNoticeShouldBeScheduledWhenCreatedRequestIsSetToExpire() {
    JsonObject requestNotice = new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withRequestExpirationEvent()
      .withBeforeTiming(Period.days(3))
      .sendInRealTime(true)
      .create();

    NoticePolicyBuilder noticePolicyBuilder = new NoticePolicyBuilder()
      .withName("request policy")
      .withRequestNotices(Collections.singletonList(requestNotice));

    useDefaultRollingPoliciesAndOnlyAllowPageRequests(noticePolicyBuilder);

    final var requestExpiration = java.time.LocalDate.now(getClockManager().getClock()).plusMonths(3);

    IndividualResource request = requestsFixture.place(new RequestBuilder().page()
      .forItem(item)
      .withRequesterId(requester.getId())
      .withRequestDate(DateTime.now())
      .withStatus(OPEN_NOT_YET_FILLED)
      .withPickupServicePoint(pickupServicePoint)
      .withRequestExpiration(requestExpiration));

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(scheduledNoticesClient::getAll, hasSize(1));

    List<JsonObject> scheduledNotices = scheduledNoticesClient.getAll();
    assertThat(scheduledNotices.size(), is(1));

    JsonObject scheduledNotice = scheduledNotices.get(0);
    JsonObject noticeConfig = scheduledNotice.getJsonObject("noticeConfig");

    assertThat(scheduledNotice.getString("requestId"), is(request.getId().toString()));
    assertThat(scheduledNotice.getString("triggeringEvent"), is("Request expiration"));
    assertThat(scheduledNotice.getString("nextRunTime"), isEquivalentTo(ZonedDateTime.of(
      requestExpiration.atTime(23, 59, 59), UTC).minusDays(3)));
    assertThat(noticeConfig.getString("timing"), is("Before"));
    assertThat(noticeConfig.getString("templateId"), is(templateId.toString()));
    assertThat(noticeConfig.getString("format"), is("Email"));
    assertThat(noticeConfig.getBoolean("sendInRealTime"), is(true));
  }

  @Test
  public void requestExpirationUponAtNoticeShouldBeRescheduledWhenUpdatedRequestIsSetToExpire() {
    JsonObject requestNotice = new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withRequestExpirationEvent()
      .withUponAtTiming()
      .sendInRealTime(true)
      .create();

    NoticePolicyBuilder noticePolicyBuilder = new NoticePolicyBuilder()
      .withName("request policy")
      .withRequestNotices(Collections.singletonList(requestNotice));

    useDefaultRollingPoliciesAndOnlyAllowPageRequests(noticePolicyBuilder);

    final var requestExpiration = java.time.LocalDate.now(getClockManager().getClock()).plusMonths(3);

    RequestBuilder requestBuilder = new RequestBuilder().page()
      .forItem(item)
      .withRequesterId(requester.getId())
      .withRequestDate(DateTime.now())
      .withStatus(OPEN_NOT_YET_FILLED)
      .withPickupServicePoint(pickupServicePoint)
      .withRequestExpiration(requestExpiration);
    IndividualResource request = requestsFixture.place(requestBuilder);

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(scheduledNoticesClient::getAll, hasSize(1));

    List<JsonObject> scheduledNotices = scheduledNoticesClient.getAll();
    assertThat(scheduledNotices.size(), is(1));

    JsonObject scheduledNotice = scheduledNotices.get(0);
    JsonObject noticeConfig = scheduledNotice.getJsonObject("noticeConfig");

    assertThat(scheduledNotice.getString("requestId"), is(request.getId().toString()));
    assertThat(scheduledNotice.getString("triggeringEvent"), is("Request expiration"));
    assertThat(scheduledNotice.getString("nextRunTime"),
      isEquivalentTo(ZonedDateTime.of(requestExpiration.atTime(23, 59, 59), UTC)));
    assertThat(noticeConfig.getString("timing"), is("Upon At"));
    assertThat(noticeConfig.getString("templateId"), is(templateId.toString()));
    assertThat(noticeConfig.getString("format"), is("Email"));
    assertThat(noticeConfig.getBoolean("sendInRealTime"), is(true));

    requestsClient.replace(request.getId(), requestBuilder
      .withRequestExpiration(requestExpiration.plusDays(1)));

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(scheduledNoticesClient::getAll, hasSize(1));

    scheduledNotices = scheduledNoticesClient.getAll();
    assertThat(scheduledNotices.size(), is(1));

    scheduledNotice = scheduledNotices.get(0);
    noticeConfig = scheduledNotice.getJsonObject("noticeConfig");

    assertThat(scheduledNotice.getString("requestId"), is(request.getId().toString()));
    assertThat(scheduledNotice.getString("triggeringEvent"), is("Request expiration"));
    assertThat(scheduledNotice.getString("nextRunTime"),
      isEquivalentTo(ZonedDateTime.of(requestExpiration.atTime(23, 59, 59), UTC).plusDays(1)));
    assertThat(noticeConfig.getString("timing"), is("Upon At"));
    assertThat(noticeConfig.getString("templateId"), is(templateId.toString()));
    assertThat(noticeConfig.getString("format"), is("Email"));
    assertThat(noticeConfig.getBoolean("sendInRealTime"), is(true));
  }

  @Test
  public void recurringRequestExpirationNoticeShouldBeDeletedWhenExpirationDateIsRemovedDuringUpdate() {
    JsonObject requestNotice = new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withRequestExpirationEvent()
      .withBeforeTiming(Period.days(3))
      .recurring(Period.days(1))
      .sendInRealTime(true)
      .create();

    NoticePolicyBuilder noticePolicyBuilder = new NoticePolicyBuilder()
      .withName("request policy")
      .withRequestNotices(Collections.singletonList(requestNotice));

    useDefaultRollingPoliciesAndOnlyAllowPageRequests(noticePolicyBuilder);

    final var requestExpiration = java.time.LocalDate.now(getClockManager().getClock()).plusMonths(3);

    RequestBuilder requestBuilder = new RequestBuilder().page()
      .forItem(item)
      .withRequesterId(requester.getId())
      .withRequestDate(DateTime.now())
      .withStatus(OPEN_NOT_YET_FILLED)
      .withPickupServicePoint(pickupServicePoint)
      .withRequestExpiration(requestExpiration);
    IndividualResource request = requestsFixture.place(requestBuilder);

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(scheduledNoticesClient::getAll, hasSize(1));

    List<JsonObject> scheduledNotices = scheduledNoticesClient.getAll();
    assertThat(scheduledNotices.size(), is(1));

    JsonObject scheduledNotice = scheduledNotices.get(0);
    JsonObject noticeConfig = scheduledNotice.getJsonObject("noticeConfig");

    assertThat(scheduledNotice.getString("requestId"), is(request.getId().toString()));
    assertThat(scheduledNotice.getString("triggeringEvent"), is("Request expiration"));
    assertThat(scheduledNotice.getString("nextRunTime"), isEquivalentTo(ZonedDateTime.of(
      requestExpiration.atTime(23, 59, 59), UTC).minusDays(3)));
    assertThat(noticeConfig.getString("timing"), is("Before"));
    assertThat(noticeConfig.getString("templateId"), is(templateId.toString()));
    assertThat(noticeConfig.getString("format"), is("Email"));
    assertThat(noticeConfig.getBoolean("sendInRealTime"), is(true));

    requestsClient.replace(request.getId(), requestBuilder.withNoRequestExpiration());

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(scheduledNoticesClient::getAll, hasSize(0));
  }

  @Test
  public void holdShelfExpirationNoticeShouldBeScheduledOnCheckIn() {
    JsonObject requestNotice = new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withHoldShelfExpirationEvent()
      .withUponAtTiming()
      .sendInRealTime(true)
      .create();

    NoticePolicyBuilder noticePolicyBuilder = new NoticePolicyBuilder()
      .withName("request policy")
      .withRequestNotices(Collections.singletonList(requestNotice));

    useDefaultRollingPoliciesAndOnlyAllowPageRequests(noticePolicyBuilder);

    final var requestExpiration = java.time.LocalDate.now(getClockManager().getClock()).plusMonths(3);

    RequestBuilder requestBuilder = new RequestBuilder().page()
      .forItem(item)
      .withRequesterId(requester.getId())
      .withRequestDate(DateTime.now())
      .withStatus(OPEN_NOT_YET_FILLED)
      .withPickupServicePoint(pickupServicePoint)
      .withRequestExpiration(requestExpiration);
    IndividualResource request = requestsFixture.place(requestBuilder);

    CheckInByBarcodeRequestBuilder checkInByBarcodeRequestBuilder =
      new CheckInByBarcodeRequestBuilder()
      .forItem(item)
      .withItemBarcode(item.getBarcode())
      .at(pickupServicePoint);

    checkInFixture.checkInByBarcode(checkInByBarcodeRequestBuilder);

    String holdShelfExpirationDate = requestsClient.get(request)
      .getJson()
      .getString(HOLD_SHELF_EXPIRATION_DATE);

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(scheduledNoticesClient::getAll, hasSize(1));

    List<JsonObject> scheduledNotices = scheduledNoticesClient.getAll();
    assertThat(scheduledNotices.size(), is(1));

    JsonObject scheduledNotice = scheduledNotices.get(0);
    JsonObject noticeConfig = scheduledNotice.getJsonObject("noticeConfig");

    assertThat(scheduledNotice.getString("requestId"), is(request.getId().toString()));
    assertThat(scheduledNotice.getString("triggeringEvent"), is("Hold expiration"));
    assertThat(scheduledNotice.getString("nextRunTime"), is(holdShelfExpirationDate));
    assertThat(noticeConfig.getString("timing"), is("Upon At"));
    assertThat(noticeConfig.getString("templateId"), is(templateId.toString()));
    assertThat(noticeConfig.getString("format"), is("Email"));
    assertThat(noticeConfig.getBoolean("sendInRealTime"), is(true));
  }

  @Test
  public void requestExpirationAndHoldShelfExpirationNoticesAreCreatedWhenPickupReminderIsFirstInPolicy() {
    JsonObject pickupReminder = new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withAvailableEvent()
      .sendInRealTime(true)
      .create();

    JsonObject holdShelfExpirationNotice = new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withHoldShelfExpirationEvent()
      .withUponAtTiming()
      .sendInRealTime(true)
      .create();

    JsonObject requestExpirationNotice = new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withRequestExpirationEvent()
      .withUponAtTiming()
      .sendInRealTime(true)
      .create();

    NoticePolicyBuilder noticePolicyBuilder = new NoticePolicyBuilder()
      .withName("request policy")
      .withRequestNotices(Arrays.asList(pickupReminder, holdShelfExpirationNotice, requestExpirationNotice));

    useDefaultRollingPoliciesAndOnlyAllowPageRequests(noticePolicyBuilder);

    final var requestExpiration = java.time.LocalDate.now(getClockManager().getClock()).plusMonths(3);

    RequestBuilder requestBuilder = new RequestBuilder().page()
      .forItem(item)
      .withRequesterId(requester.getId())
      .withRequestDate(DateTime.now())
      .withStatus(OPEN_NOT_YET_FILLED)
      .withPickupServicePoint(pickupServicePoint)
      .withRequestExpiration(requestExpiration);

    requestsFixture.place(requestBuilder);

    CheckInByBarcodeRequestBuilder checkInByBarcodeRequestBuilder =
      new CheckInByBarcodeRequestBuilder()
        .forItem(item)
        .withItemBarcode(item.getBarcode())
        .at(pickupServicePoint);

    checkInFixture.checkInByBarcode(checkInByBarcodeRequestBuilder);

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(scheduledNoticesClient::getAll, hasSize(2));

    List<JsonObject> scheduledNotices = scheduledNoticesClient.getAll();
    assertThat(scheduledNotices.size(), is(2));

    List<String> triggeringEvents = scheduledNotices.stream()
      .map(n -> n.getString("triggeringEvent"))
      .collect(Collectors.toList());

    assertThat(triggeringEvents, containsInAnyOrder("Request expiration", "Hold expiration"));
  }

  private ZonedDateTime toZonedStartOfDay(java.time.LocalDate date) {
    final var startOfDay = date.atStartOfDay();

    return ZonedDateTime.of(startOfDay, ZoneId.systemDefault().normalized());
  }
}
