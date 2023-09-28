package api.requests.scenarios;

import static api.support.PubsubPublisherTestUtils.assertThatPublishedNoticeLogRecordEventsAreValid;
import static api.support.builders.RequestBuilder.OPEN_NOT_YET_FILLED;
import static api.support.matchers.ItemStatusCodeMatcher.hasItemStatus;
import static api.support.matchers.ValidationErrorMatchers.hasCode;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfPublishedEvents;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfSentNotices;
import static java.time.ZoneOffset.UTC;
import static java.util.Arrays.asList;
import static org.folio.circulation.domain.RequestStatus.CLOSED_CANCELLED;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE_ERROR;
import static org.folio.circulation.support.utils.ClockUtil.getZonedDateTime;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.ErrorCode;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.utils.ClockUtil;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import api.support.APITests;
import api.support.MultipleJsonRecords;
import api.support.TlrFeatureStatus;
import api.support.builders.NoticeConfigurationBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.builders.RequestBuilder;
import api.support.fakes.FakeModNotify;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import io.vertx.core.json.JsonObject;

class CancelRequestTests extends APITests {
  @Test
  void canCancelRequest() {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource james = usersFixture.james();
    final IndividualResource jessica = usersFixture.jessica();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, james);

    final IndividualResource requestByJessica = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, jessica, ClockUtil.getZonedDateTime().minusHours(5));

    requestsFixture.cancelRequest(requestByJessica);

    final JsonObject cancelledRequest = requestsClient.get(requestByJessica).getJson();

    assertThat("Should be cancelled",
      cancelledRequest.getString("status"), is("Closed - Cancelled"));

    assertThat("Should not have a position",
      cancelledRequest.containsKey("position"), is(false));

    assertThat("Should retain stored item summary",
      cancelledRequest.containsKey("item"), is(true));

    assertThat("Should retain stored requester summary",
      cancelledRequest.containsKey("requester"), is(true));
  }

  @Test
  void canCancelRequestInMiddleOfTheQueue() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource steve = usersFixture.steve();
    IndividualResource charlotte = usersFixture.charlotte();
    IndividualResource rebecca = usersFixture.rebecca();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, james);

    final IndividualResource requestByJessica = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, jessica, ClockUtil.getZonedDateTime().minusHours(5));

    final IndividualResource requestBySteve = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, steve, ClockUtil.getZonedDateTime().minusHours(4));

    final IndividualResource requestByCharlotte = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, charlotte, ClockUtil.getZonedDateTime().minusHours(3));

    final IndividualResource requestByRebecca = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, rebecca, ClockUtil.getZonedDateTime().minusHours(2));

    requestsFixture.cancelRequest(requestBySteve);

    MultipleRecords<JsonObject> queue = requestsFixture.getQueueFor(smallAngryPlanet);

    assertThat(queue.getTotalRecords(), is(3));

    final Collection<Integer> positions = queue
      .mapRecords(request -> request.getInteger("position"))
      .getRecords();

    assertThat("Should have contiguous positions", positions, contains(1, 2, 3));

    final Collection<UUID> requestIds = queue
      .mapRecords(request -> UUID.fromString(request.getString("id")))
      .getRecords();

    assertThat("Should be in same order as before cancellation", requestIds, contains(
      requestByJessica.getId(), requestByCharlotte.getId(), requestByRebecca.getId()));
  }

  @Test
  void canCancelRequestAtTheBeginningOfTheQueue() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource steve = usersFixture.steve();
    IndividualResource charlotte = usersFixture.charlotte();
    IndividualResource rebecca = usersFixture.rebecca();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, james);

    final IndividualResource requestByJessica = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, jessica, ClockUtil.getZonedDateTime().minusHours(5));

    final IndividualResource requestBySteve = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, steve, ClockUtil.getZonedDateTime().minusHours(4));

    final IndividualResource requestByCharlotte = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, charlotte, ClockUtil.getZonedDateTime().minusHours(3));

    final IndividualResource requestByRebecca = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, rebecca, ClockUtil.getZonedDateTime().minusHours(2));

    requestsFixture.cancelRequest(requestByJessica);

    MultipleRecords<JsonObject> queue = requestsFixture.getQueueFor(smallAngryPlanet);

    assertThat(queue.getTotalRecords(), is(3));

    final Collection<Integer> positions = queue
      .mapRecords(request -> request.getInteger("position"))
      .getRecords();

    assertThat("Should be in contiguous positions", positions, contains(1, 2, 3));

    final Collection<UUID> requestIds = queue
      .mapRecords(request -> UUID.fromString(request.getString("id")))
      .getRecords();

    assertThat("Should be in same order as before cancellation", requestIds, contains(
      requestBySteve.getId(), requestByCharlotte.getId(), requestByRebecca.getId()));
  }

  @Test
  void canCancelRequestAtTheEndOfTheQueue() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource steve = usersFixture.steve();
    IndividualResource charlotte = usersFixture.charlotte();
    IndividualResource rebecca = usersFixture.rebecca();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, james);

    final IndividualResource requestByJessica = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, jessica, ClockUtil.getZonedDateTime().minusHours(5));

    final IndividualResource requestBySteve = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, steve, ClockUtil.getZonedDateTime().minusHours(4));

    final IndividualResource requestByCharlotte = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, charlotte, ClockUtil.getZonedDateTime().minusHours(3));

    final IndividualResource requestByRebecca = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, rebecca, ClockUtil.getZonedDateTime().minusHours(2));

    requestsFixture.cancelRequest(requestByRebecca);

    MultipleRecords<JsonObject> queue = requestsFixture.getQueueFor(smallAngryPlanet);

    assertThat(queue.getTotalRecords(), is(3));

    final Collection<Integer> positions = queue
      .mapRecords(request -> request.getInteger("position"))
      .getRecords();

    assertThat("Should be in contiguous positions", positions, contains(1, 2, 3));

    final Collection<UUID> requestIds = queue
      .mapRecords(request -> UUID.fromString(request.getString("id")))
      .getRecords();

    assertThat("Should be in same order as before cancellation", requestIds, contains(
      requestByJessica.getId(), requestBySteve.getId(), requestByCharlotte.getId()));
  }

  /**
   * In order for items to appear on the hold shelf clearance report they need
   * to retain the request fulfilment related status after being cancelled
   */
  @Test
  void cancellingAPartiallyFulfilledPageRequestShouldNotChangeItemStatus() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource jessica = usersFixture.jessica();

    final IndividualResource requestByJessica = requestsFixture.place(
      new RequestBuilder()
      .page()
      .forItem(smallAngryPlanet)
      .by(jessica)
      .fulfillToHoldShelf(servicePointsFixture.cd1()));

    checkInFixture.checkInByBarcode(smallAngryPlanet);

    requestsFixture.cancelRequest(requestByJessica);

    final IndividualResource itemAfterCancellation = itemsClient.get(smallAngryPlanet);

    assertThat(itemAfterCancellation, hasItemStatus("Awaiting pickup"));
  }

  @Test
  void patronNoticeIsNotSentWhenPatronNoticeRequestFails() {
    UUID requestCancelledTemplateId = UUID.randomUUID();

    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("test policy")
      .withLoanNotices(Collections.singletonList(new NoticeConfigurationBuilder()
        .withTemplateId(requestCancelledTemplateId)
        .withEventType("Request cancellation")
        .create()));

    useFallbackPolicies(
      loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicy).getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());

    UUID id = UUID.randomUUID();
    UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    ItemResource item = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource requester = usersFixture.steve();
    ZonedDateTime requestDate = ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC);
    IndividualResource request = requestsFixture.place(new RequestBuilder()
      .withId(id)
      .open()
      .page()
      .forItem(item)
      .by(requester)
      .withRequestDate(requestDate)
      .fulfillToHoldShelf()
      .withRequestExpiration(LocalDate.of(2017, 7, 30))
      .withHoldShelfExpiration(LocalDate.of(2017, 8, 31))
      .withPickupServicePointId(pickupServicePointId)
      .withTags(new RequestBuilder.Tags(asList("new", "important"))));

    verifyNumberOfSentNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);

    FakeModNotify.setFailPatronNoticesWithBadRequest(true);

    requestsFixture.cancelRequest(request);

    verifyNumberOfSentNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @Test
  void shouldAllowToCancelRequestWithNoPosition() {
    IndividualResource requesterId = usersFixture.rebecca();
    final ItemResource nod = itemsFixture.basedUponNod();

    checkOutFixture.checkOutByBarcode(nod, requesterId);

    IndividualResource firstHoldRequest = holdRequestWithNoPosition(nod,
      usersFixture.steve());
    IndividualResource secondHoldRequest = holdRequestWithNoPosition(nod,
      usersFixture.charlotte());

    requestsFixture.cancelRequest(firstHoldRequest);
    requestsFixture.cancelRequest(secondHoldRequest);

    MultipleJsonRecords allRequests = requestsFixture.getAllRequests();

    assertThat(allRequests.totalRecords(), is(2));

    JsonObject firstRequest = allRequests.getById(firstHoldRequest.getId());
    JsonObject secondRequest = allRequests.getById(secondHoldRequest.getId());

    assertThat(firstRequest.getString("status"), is(CLOSED_CANCELLED.getValue()));
    assertThat(secondRequest.getString("status"), is(CLOSED_CANCELLED.getValue()));
  }

  @Test
  void titleLevelRequestCancellationNoticeShouldBeSentWithEnabledTlr() {
    UUID templateId = UUID.randomUUID();
    templateFixture.createDummyNoticeTemplate(templateId);
    reconfigureTlrFeature(TlrFeatureStatus.ENABLED, null, templateId, null);

    IndividualResource request = requestsFixture.place(buildTitleLevelRequest());
    verifyNumberOfSentNotices(0);
    requestsFixture.cancelRequest(request);
    var notices = verifyNumberOfSentNotices(1);
    assertThatPublishedNoticeLogRecordEventsAreValid(notices.get(0));
  }

  @ParameterizedTest
  @EnumSource(value = TlrFeatureStatus.class, names = {"DISABLED", "NOT_CONFIGURED"})
  void titleLevelRequestCancellationNoticeShouldNotBeSentWithDisabledTlr(
    TlrFeatureStatus tlrFeatureStatus) {

    UUID templateId = UUID.randomUUID();
    templateFixture.createDummyNoticeTemplate(templateId);
    reconfigureTlrFeature(TlrFeatureStatus.ENABLED, null, templateId, null);
    IndividualResource request = requestsFixture.place(buildTitleLevelRequest());
    verifyNumberOfSentNotices(0);

    reconfigureTlrFeature(tlrFeatureStatus, null, templateId, null);
    Response response = requestsFixture.attemptCancelRequest(request);

    assertThat(response.getStatusCode(), CoreMatchers.is(422));
    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Request level must be one of the following: \"Item\""),
      hasCode(ErrorCode.REQUEST_LEVEL_IS_NOT_ALLOWED))));
    verifyNumberOfSentNotices(0);
  }

  @Test
  void titleLevelRequestCancellationNoticeShouldNotBeSentWithoutConfiguredTemplate() {
    reconfigureTlrFeature(TlrFeatureStatus.ENABLED, null, null, null);

    IndividualResource request = requestsFixture.place(buildTitleLevelRequest());
    verifyNumberOfSentNotices(0);
    requestsFixture.cancelRequest(request);
    verifyNumberOfSentNotices(0);
  }

  private IndividualResource holdRequestWithNoPosition(
    IndividualResource item, IndividualResource requester) {

    JsonObject request = new RequestBuilder()
      .open()
      .hold()
      .forItem(item)
      .by(requester)
      .withPickupServicePoint(servicePointsFixture.cd1())
      .create();

    request.remove("position");

    return requestsStorageClient.create(request);
  }

  private RequestBuilder buildTitleLevelRequest() {
    UUID instanceId = instancesFixture.basedUponDunkirk().getId();
    holdingsFixture.defaultWithHoldings(instanceId);

    return new RequestBuilder()
      .hold()
      .titleRequestLevel()
      .withNoItemId()
      .withNoHoldingsRecordId()
      .withInstanceId(instanceId)
      .withRequesterId(usersFixture.charlotte().getId())
      .withRequestDate(getZonedDateTime())
      .withStatus(OPEN_NOT_YET_FILLED)
      .withPickupServicePoint(servicePointsFixture.cd1());
  }
}
