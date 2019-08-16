package api.requests;

import static api.support.builders.RequestBuilder.OPEN_NOT_YET_FILLED;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static org.folio.circulation.domain.representations.RequestProperties.HOLD_SHELF_EXPIRATION_DATE;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.net.MalformedURLException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.awaitility.Awaitility;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
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
import api.support.http.InventoryItemResource;
import io.vertx.core.json.JsonObject;

public class RequestScheduledNoticesTests extends APITests {

  private UUID templateId = UUID.randomUUID();
  private InventoryItemResource item;
  private IndividualResource requester;
  private IndividualResource pickupServicePoint;

  @Before
  public void beforeEach()
    throws MalformedURLException,
    InterruptedException,
    TimeoutException,
    ExecutionException {

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
  public void requestExpirationUponAtNoticeShouldBeScheduledWhenCreatedRequestIsSetToExpire()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    JsonObject requestNotice = new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withRequestExpirationEvent()
      .withUponAtTiming()
      .sendInRealTime(true)
      .create();

    NoticePolicyBuilder noticePolicyBuilder = new NoticePolicyBuilder()
      .withName("request policy")
      .withRequestNotices(Collections.singletonList(requestNotice));

    useLoanPolicyAsFallback(
      loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.pageRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicyBuilder).getId());

    LocalDate requestExpiration = LocalDate.now().plusMonths(3);
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
      isEquivalentTo(requestExpiration.toDateTimeAtStartOfDay()));
    assertThat(noticeConfig.getString("timing"), is("Upon At"));
    assertThat(noticeConfig.getString("templateId"), is(templateId.toString()));
    assertThat(noticeConfig.getString("format"), is("Email"));
    assertThat(noticeConfig.getBoolean("sendInRealTime"), is(true));
  }

  @Test
  public void requestExpirationNoticeShouldNotBeScheduledWhenCreatedRequestDoesNotExpire()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    JsonObject requestNotice = new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withRequestExpirationEvent()
      .withUponAtTiming()
      .sendInRealTime(true)
      .create();

    NoticePolicyBuilder noticePolicyBuilder = new NoticePolicyBuilder()
      .withName("request policy")
      .withRequestNotices(Collections.singletonList(requestNotice));

    useLoanPolicyAsFallback(
      loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.pageRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicyBuilder).getId());

    requestsFixture.place(new RequestBuilder().page()
      .forItem(item)
      .withRequesterId(requester.getId())
      .withRequestDate(DateTime.now())
      .withStatus(OPEN_NOT_YET_FILLED)
      .withPickupServicePoint(pickupServicePoint)
      .withRequestExpiration(null));

    TimeUnit.SECONDS.sleep(1);

    List<JsonObject> scheduledNotices = scheduledNoticesClient.getAll();
    assertThat(scheduledNotices, hasSize(0));
  }

  @Test
  public void requestExpirationBeforeNoticeShouldBeScheduledWhenCreatedRequestIsSetToExpire()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    JsonObject requestNotice = new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withRequestExpirationEvent()
      .withBeforeTiming(Period.days(3))
      .sendInRealTime(true)
      .create();

    NoticePolicyBuilder noticePolicyBuilder = new NoticePolicyBuilder()
      .withName("request policy")
      .withRequestNotices(Collections.singletonList(requestNotice));

    useLoanPolicyAsFallback(
      loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.pageRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicyBuilder).getId());

    LocalDate requestExpiration = LocalDate.now().plusMonths(3);
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
      isEquivalentTo(requestExpiration.toDateTimeAtStartOfDay().minusDays(3)));
    assertThat(noticeConfig.getString("timing"), is("Before"));
    assertThat(noticeConfig.getString("templateId"), is(templateId.toString()));
    assertThat(noticeConfig.getString("format"), is("Email"));
    assertThat(noticeConfig.getBoolean("sendInRealTime"), is(true));
  }

  @Test
  public void requestExpirationUponAtNoticeShouldBeRescheduledWhenUpdatedRequestIsSetToExpire()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    JsonObject requestNotice = new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withRequestExpirationEvent()
      .withUponAtTiming()
      .sendInRealTime(true)
      .create();

    NoticePolicyBuilder noticePolicyBuilder = new NoticePolicyBuilder()
      .withName("request policy")
      .withRequestNotices(Collections.singletonList(requestNotice));

    useLoanPolicyAsFallback(
      loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.pageRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicyBuilder).getId());

    LocalDate requestExpiration = LocalDate.now(DateTimeZone.UTC).plusMonths(3);
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
      isEquivalentTo(requestExpiration.toDateTimeAtStartOfDay()));
    assertThat(noticeConfig.getString("timing"), is("Upon At"));
    assertThat(noticeConfig.getString("templateId"), is(templateId.toString()));
    assertThat(noticeConfig.getString("format"), is("Email"));
    assertThat(noticeConfig.getBoolean("sendInRealTime"), is(true));

    requestsClient.replace(request.getId(), requestBuilder.withRequestExpiration(requestExpiration.plusDays(1)));

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
      isEquivalentTo(requestExpiration.toDateTimeAtStartOfDay().plusDays(1)));
    assertThat(noticeConfig.getString("timing"), is("Upon At"));
    assertThat(noticeConfig.getString("templateId"), is(templateId.toString()));
    assertThat(noticeConfig.getString("format"), is("Email"));
    assertThat(noticeConfig.getBoolean("sendInRealTime"), is(true));
  }

  @Test
  public void holdShelfExpirationNoticeShouldBeScheduledOnCheckIn()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    JsonObject requestNotice = new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withHoldShelfExpirationEvent()
      .withUponAtTiming()
      .sendInRealTime(true)
      .create();

    NoticePolicyBuilder noticePolicyBuilder = new NoticePolicyBuilder()
      .withName("request policy")
      .withRequestNotices(Collections.singletonList(requestNotice));

    useLoanPolicyAsFallback(
      loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.pageRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicyBuilder).getId());

    LocalDate requestExpiration = LocalDate.now().plusMonths(3);
    RequestBuilder requestBuilder = new RequestBuilder().page()
      .forItem(item)
      .withRequesterId(requester.getId())
      .withRequestDate(DateTime.now())
      .withStatus(OPEN_NOT_YET_FILLED)
      .withPickupServicePoint(pickupServicePoint)
      .withRequestExpiration(requestExpiration);
    IndividualResource request = requestsFixture.place(requestBuilder);

    CheckInByBarcodeRequestBuilder at = new CheckInByBarcodeRequestBuilder()
      .forItem(item)
      .withItemBarcode(item.getBarcode())
      .at(pickupServicePoint);

    loansFixture.checkInByBarcode(at);
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
    assertThat(scheduledNotice.getString("triggeringEvent"), is("Hold Expiration"));
    assertThat(scheduledNotice.getString("nextRunTime"), is(holdShelfExpirationDate));
    assertThat(noticeConfig.getString("timing"), is("Upon At"));
    assertThat(noticeConfig.getString("templateId"), is(templateId.toString()));
    assertThat(noticeConfig.getString("format"), is("Email"));
    assertThat(noticeConfig.getBoolean("sendInRealTime"), is(true));
  }
}
