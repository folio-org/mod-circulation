package api.queue;

import static api.support.fakes.PublishedEvents.byLogEventType;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static java.util.stream.Collectors.toList;
import static org.folio.circulation.domain.representations.logs.LogEventType.REQUEST_REORDERED;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.awaitility.Awaitility;
import org.folio.circulation.support.http.client.Response;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import api.support.APITests;
import api.support.TlrFeatureStatus;
import api.support.builders.ReorderQueueBuilder;
import api.support.builders.RequestBuilder;
import api.support.fakes.FakePubSub;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class RequestQueueResourceTest extends APITests {
  private ItemResource item;

  private List<ItemResource> items;
  private UUID instanceId;

  private IndividualResource jessica;
  private IndividualResource steve;
  private IndividualResource james;
  private IndividualResource rebecca;
  private IndividualResource charlotte;

  @BeforeEach
  public void setUp() {
    item = itemsFixture.basedUponSmallAngryPlanet();

    items = itemsFixture.createMultipleItemsForTheSameInstance(3);
    instanceId = items.get(0).getInstanceId();

    jessica = usersFixture.jessica();
    steve = usersFixture.steve();
    james = usersFixture.james();
    rebecca = usersFixture.rebecca();
    charlotte = usersFixture.charlotte();
  }

  @Test
  void validationErrorWhenTlrEnabledAndReorderingQueueForItem() {
    reconfigureTlrFeature(TlrFeatureStatus.ENABLED);

    Response response = requestQueueFixture.attemptReorderQueueForItem(
      UUID.randomUUID().toString(),
      new ReorderQueueBuilder()
        .addReorderRequest(UUID.randomUUID().toString(), 1)
        .create());

    assertThat(response.getStatusCode(), is(422));
    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Refuse to reorder request queue, TLR feature status is ENABLED."))));
  }

  @ParameterizedTest
  @EnumSource(value = TlrFeatureStatus.class, names = {"DISABLED", "NOT_CONFIGURED"})
  void validationErrorWhenTlrDisabledAndReorderingQueueForInstance(
    TlrFeatureStatus tlrFeatureStatus) {

    reconfigureTlrFeature(tlrFeatureStatus);

    Response response = requestQueueFixture.attemptReorderQueueForInstance(
      UUID.randomUUID().toString(),
      new ReorderQueueBuilder()
        .addReorderRequest(UUID.randomUUID().toString(), 1)
        .create());

    assertThat(response.getStatusCode(), is(422));
    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Refuse to reorder request queue, TLR feature status is DISABLED."))));
  }

  @Test
  void notFoundErrorWhenItemDoesNotExists() {
    Response response = requestQueueFixture.attemptReorderQueueForItem(
      UUID.randomUUID().toString(),
      new ReorderQueueBuilder()
        .addReorderRequest(UUID.randomUUID().toString(), 1)
        .create());

    assertThat(response.getStatusCode(), is(404));
    assertTrue(response.getBody()
      .matches("Item record with ID .+ cannot be found"));
  }

  @Test
  void notFoundErrorWhenInstanceDoesNotExists() {
    reconfigureTlrFeature(TlrFeatureStatus.ENABLED);

    Response response = requestQueueFixture.attemptReorderQueueForInstance(
      UUID.randomUUID().toString(),
      new ReorderQueueBuilder()
        .addReorderRequest(UUID.randomUUID().toString(), 1)
        .create());

    assertThat(response.getStatusCode(), is(404));
    assertTrue(response.getBody()
      .matches("Instance record with ID .+ cannot be found"));
  }

  @Test
  void refuseAttemptToMovePageRequestFromFirstPosition() {
    IndividualResource pageRequest = pageRequestForDefaultItem(steve);
    IndividualResource recallRequest = recallRequestForDefaultItem(jessica);

    Response response = requestQueueFixture.attemptReorderQueueForItem(
      item.getId().toString(),
      new ReorderQueueBuilder()
        .addReorderRequest(recallRequest.getId().toString(), 1)
        .addReorderRequest(pageRequest.getId().toString(), 2)
        .create());

    verifyValidationFailure(response,
      is("Page requests can not be displaced from position 1."));
  }

  @Test
  void refuseAttemptToMovePageRequestFromOneOfTheTopPositionsWhenTlrEnabled() {
    reconfigureTlrFeature(TlrFeatureStatus.ENABLED);

    // It is possible to have multiple page requests in the unified queue when TLR feature is
    // enabled
    IndividualResource pageRequestBySteve = pageRequestForItem(steve, items.get(0));
    IndividualResource pageRequestByJames = pageRequestForItem(james, items.get(1));
    IndividualResource pageRequestByCharlotte = pageTitleLevelRequestForItem(charlotte, items.get(2));
    IndividualResource recallRequestByJessica = recallRequestForItem(jessica, items.get(1));

    Response response = requestQueueFixture.attemptReorderQueueForInstance(
      instanceId.toString(),
      new ReorderQueueBuilder()
        .addReorderRequest(pageRequestBySteve.getId().toString(), 1)
        .addReorderRequest(pageRequestByJames.getId().toString(), 2)
        .addReorderRequest(recallRequestByJessica.getId().toString(), 3)
        .addReorderRequest(pageRequestByCharlotte.getId().toString(), 4)
        .create());

    verifyValidationFailure(response,
      is("Page requests can not be displaced from top positions."));
  }

  @Test
  void refuseAttemptToMoveRequestBeingFulfilledFromFirstPosition() {
    checkOutFixture.checkOutByBarcode(item, usersFixture.rebecca());

    IndividualResource inFulfillmentRequest = inFulfillmentRecallRequestForDefaultItem(steve);
    IndividualResource recallRequest = recallRequestForDefaultItem(jessica);

    Response response = requestQueueFixture.attemptReorderQueueForItem(
      item.getId().toString(),
      new ReorderQueueBuilder()
        .addReorderRequest(recallRequest.getId().toString(), 1)
        .addReorderRequest(inFulfillmentRequest.getId().toString(), 2)
        .create());

    verifyValidationFailure(response,
      is("Requests can not be displaced from position 1 when fulfillment begun."));
  }

  @Test
  void refuseAttemptToMoveRequestBeingFulfilledFromOneOfTheTopPositionsWhenTlrEnabled() {
    reconfigureTlrFeature(TlrFeatureStatus.ENABLED);

    checkOutFixture.checkOutByBarcode(items.get(0), usersFixture.rebecca());
    checkOutFixture.checkOutByBarcode(items.get(1), usersFixture.rebecca());

    // It is possible to have multiple requests in fulfillment process in the unified queue when
    // TLR feature is enabled
    IndividualResource inFulfillmentRequestBySteve = inFulfillmentRecallRequestForItem(steve,
      items.get(0));
    IndividualResource inFulfillmentRequestByJames = inFulfillmentRecallRequestForItem(james,
      items.get(1));
    IndividualResource inFulfillmentRequestByCharlotte =
      inFulfillmentRecallTitleLevelRequestForItem(charlotte, items.get(2));
    IndividualResource recallRequestByJessica = recallRequestForItem(jessica, items.get(1));

    Response response = requestQueueFixture.attemptReorderQueueForInstance(
      instanceId.toString(),
      new ReorderQueueBuilder()
        .addReorderRequest(inFulfillmentRequestBySteve.getId().toString(), 1)
        .addReorderRequest(inFulfillmentRequestByJames.getId().toString(), 2)
        .addReorderRequest(recallRequestByJessica.getId().toString(), 3)
        .addReorderRequest(inFulfillmentRequestByCharlotte.getId().toString(), 4)
        .create());

    verifyValidationFailure(response,
      is("Requests can not be displaced from top positions when fulfillment begun."));
  }

  @ParameterizedTest
  @EnumSource(TlrFeatureStatus.class)
  void refuseAttemptToTryingToAddRequestToQueueDuringReorder(TlrFeatureStatus tlrFeatureStatus) {
    reconfigureTlrFeature(tlrFeatureStatus);

    checkOutFixture.checkOutByBarcode(item, usersFixture.rebecca());

    IndividualResource firstRecallRequest = recallRequestForDefaultItem(steve);
    IndividualResource secondRecallRequest = recallRequestForDefaultItem(jessica);

    JsonObject reorderQueueBody = new ReorderQueueBuilder()
      .addReorderRequest(secondRecallRequest.getId().toString(), 1)
      .addReorderRequest(firstRecallRequest.getId().toString(), 2)
      .addReorderRequest(UUID.randomUUID().toString(), 3)
      .create();

    Response response;
    if (tlrFeatureStatus == TlrFeatureStatus.ENABLED) {
      response = requestQueueFixture.attemptReorderQueueForInstance(
        item.getInstanceId().toString(), reorderQueueBody);
    }
    else {
      response = requestQueueFixture.attemptReorderQueueForItem(
        item.getId().toString(), reorderQueueBody);
    }

    verifyValidationFailure(response,
      is("There is inconsistency between provided reordered queue and existing queue."));
  }

  @ParameterizedTest
  @EnumSource(TlrFeatureStatus.class)
  void refuseWhenNotAllRequestsProvidedInReorderedQueue(TlrFeatureStatus tlrFeatureStatus) {
    reconfigureTlrFeature(tlrFeatureStatus);

    checkOutFixture.checkOutByBarcode(item, usersFixture.rebecca());

    holdRequestForDefaultItem(steve);

    IndividualResource secondRecallRequest = recallRequestForDefaultItem(jessica);

    JsonObject reorderQueueBody = new ReorderQueueBuilder()
      .addReorderRequest(secondRecallRequest.getId().toString(), 1)
      .create();

    Response response;
    if (tlrFeatureStatus == TlrFeatureStatus.ENABLED) {
      response = requestQueueFixture.attemptReorderQueueForInstance(
        item.getInstanceId().toString(), reorderQueueBody);
    }
    else {
      response = requestQueueFixture.attemptReorderQueueForItem(
        item.getId().toString(), reorderQueueBody);
    }

    verifyValidationFailure(response,
      is("There is inconsistency between provided reordered queue and existing queue."));
  }

  @ParameterizedTest
  @CsvSource(value = {
    "ENABLED, 1, 2, 5, 4",
    "ENABLED, 0, 2, 3, 4",
    "ENABLED, 6, 2, 3, 1",
    "ENABLED, 70, 0, 10, 2",
    "ENABLED, 1, 3, 4, 5",
    "ENABLED, 1, 2, 4, 5",
    "ENABLED, 1, 2, 3, 5",
    "DISABLED, 1, 2, 5, 4",
    "DISABLED, 0, 2, 3, 4",
    "DISABLED, 6, 2, 3, 1",
    "DISABLED, 70, 0, 10, 2",
    "DISABLED, 1, 3, 4, 5",
    "DISABLED, 1, 2, 4, 5",
    "DISABLED, 1, 2, 3, 5",
    "NOT_CONFIGURED, 1, 2, 5, 4",
    "NOT_CONFIGURED, 0, 2, 3, 4",
    "NOT_CONFIGURED, 6, 2, 3, 1",
    "NOT_CONFIGURED, 70, 0, 10, 2",
    "NOT_CONFIGURED, 1, 3, 4, 5",
    "NOT_CONFIGURED, 1, 2, 4, 5",
    "NOT_CONFIGURED, 1, 2, 3, 5",
  })
  void refuseWhenPositionsAreNotSequential(String tlrFeatureStatusString, int firstPosition,
    int secondPosition, int thirdPosition, int fourthPosition) {

    TlrFeatureStatus tlrFeatureStatus = TlrFeatureStatus.valueOf(tlrFeatureStatusString);
    reconfigureTlrFeature(tlrFeatureStatus);

    checkOutFixture.checkOutByBarcode(item, rebecca);

    IndividualResource firstHoldRequest = holdRequestForDefaultItem(steve);
    IndividualResource secondHoldRequest = holdRequestForDefaultItem(james);
    IndividualResource firstRecallRequest = recallRequestForDefaultItem(charlotte);
    IndividualResource secondRecallRequest = recallRequestForDefaultItem(jessica);

    JsonObject reorderQueueBody = new ReorderQueueBuilder()
      .addReorderRequest(firstHoldRequest.getId().toString(), firstPosition)
      .addReorderRequest(secondHoldRequest.getId().toString(), secondPosition)
      .addReorderRequest(firstRecallRequest.getId().toString(), thirdPosition)
      .addReorderRequest(secondRecallRequest.getId().toString(), fourthPosition)
      .create();

    Response response;
    if (tlrFeatureStatus == TlrFeatureStatus.ENABLED) {
      response = requestQueueFixture.attemptReorderQueueForInstance(
        item.getInstanceId().toString(), reorderQueueBody);
    }
    else {
      response = requestQueueFixture.attemptReorderQueueForItem(
        item.getId().toString(), reorderQueueBody);
    }

    verifyValidationFailure(response, is("Positions must have sequential order."));
  }

  @ParameterizedTest
  @EnumSource(TlrFeatureStatus.class)
  void refuseAttemptToReorderRequestsWithDuplicatedPositions(TlrFeatureStatus tlrFeatureStatus) {
    reconfigureTlrFeature(tlrFeatureStatus);

    checkOutFixture.checkOutByBarcode(item, usersFixture.rebecca());

    IndividualResource holdRequest = holdRequestForDefaultItem(steve);
    IndividualResource recallRequest = recallRequestForDefaultItem(jessica);

    JsonObject reorderQueueBody = new ReorderQueueBuilder()
      .addReorderRequest(recallRequest.getId().toString(), 1)
      .addReorderRequest(holdRequest.getId().toString(), 1)
      .create();

    Response response;
    if (tlrFeatureStatus == TlrFeatureStatus.ENABLED) {
      response = requestQueueFixture.attemptReorderQueueForInstance(
        item.getInstanceId().toString(), reorderQueueBody);
    }
    else {
      response = requestQueueFixture.attemptReorderQueueForItem(
        item.getId().toString(), reorderQueueBody);
    }

    verifyValidationFailure(response, is("Positions must have sequential order."));
  }

  @ParameterizedTest
  @CsvSource(value = {
    "ENABLED, 1, 2, 3, 4",
    "ENABLED, 1, 2, 4, 3",
    "ENABLED, 2, 1, 3, 4",
    "ENABLED, 2, 1, 4, 3",
    "ENABLED, 4, 3, 2, 1",
    "ENABLED, 4, 3, 1, 2",
    "ENABLED, 3, 4, 2, 1",
    "ENABLED, 3, 4, 1, 2",
    "DISABLED, 1, 2, 3, 4",
    "DISABLED, 1, 2, 4, 3",
    "DISABLED, 2, 1, 3, 4",
    "DISABLED, 2, 1, 4, 3",
    "DISABLED, 4, 3, 2, 1",
    "DISABLED, 4, 3, 1, 2",
    "DISABLED, 3, 4, 2, 1",
    "DISABLED, 3, 4, 1, 2",
    "NOT_CONFIGURED, 1, 2, 3, 4",
    "NOT_CONFIGURED, 1, 2, 4, 3",
    "NOT_CONFIGURED, 2, 1, 3, 4",
    "NOT_CONFIGURED, 2, 1, 4, 3",
    "NOT_CONFIGURED, 4, 3, 2, 1",
    "NOT_CONFIGURED, 4, 3, 1, 2",
    "NOT_CONFIGURED, 3, 4, 2, 1",
    "NOT_CONFIGURED, 3, 4, 1, 2",
  })
  void shouldReorderQueueSuccessfully(String tlrFeatureStatusString, int firstPosition,
    int secondPosition, int thirdPosition, int fourthPosition) {

    TlrFeatureStatus tlrFeatureStatus = TlrFeatureStatus.valueOf(tlrFeatureStatusString);
    reconfigureTlrFeature(tlrFeatureStatus);

    checkOutFixture.checkOutByBarcode(item, rebecca);

    IndividualResource firstHoldRequest = holdRequestForDefaultItem(steve);
    IndividualResource secondHoldRequest = holdRequestForDefaultItem(james);
    IndividualResource firstRecallRequest = recallRequestForDefaultItem(charlotte);
    IndividualResource secondRecallRequest = recallRequestForDefaultItem(jessica);

    JsonObject reorderQueueBody = new ReorderQueueBuilder()
      .addReorderRequest(firstHoldRequest.getId().toString(), firstPosition)
      .addReorderRequest(secondHoldRequest.getId().toString(), secondPosition)
      .addReorderRequest(firstRecallRequest.getId().toString(), thirdPosition)
      .addReorderRequest(secondRecallRequest.getId().toString(), fourthPosition)
      .create();

    JsonObject response;
    if (tlrFeatureStatus == TlrFeatureStatus.ENABLED) {
      response = requestQueueFixture.reorderQueueForInstance(
        item.getInstanceId().toString(), reorderQueueBody);
    }
    else {
      response = requestQueueFixture.reorderQueueForItem(
        item.getId().toString(), reorderQueueBody);
    }

    verifyQueueUpdatedForItem(reorderQueueBody, response);
  }

  @ParameterizedTest
  @CsvSource(value = {
    "1, 2, 3, 4",
    "1, 2, 4, 3",
    "2, 1, 3, 4",
    "2, 1, 4, 3",
    "4, 3, 2, 1",
    "4, 3, 1, 2",
    "3, 4, 2, 1",
    "3, 4, 1, 2"
  })
  void shouldReorderUnifiedQueueWithTitleLevelRequestsSuccessfully(int firstPosition,
    int secondPosition, int thirdPosition, int fourthPosition) {

    reconfigureTlrFeature(TlrFeatureStatus.ENABLED);

    checkOutFixture.checkOutByBarcode(items.get(0), rebecca);
    checkOutFixture.checkOutByBarcode(items.get(1), rebecca);
    checkOutFixture.checkOutByBarcode(items.get(2), rebecca);

    IndividualResource holdTlrBySteve = holdTitleLevelRequest(steve);
    IndividualResource holdIlrByJames = holdRequestForItem(james, items.get(0));
    IndividualResource recallIlrByCharlotte = recallRequestForItem(charlotte, items.get(1));
    IndividualResource recallTlrByJessica = recallTitleLevelRequest(jessica);

    JsonObject reorderQueueBody = new ReorderQueueBuilder()
      .addReorderRequest(holdTlrBySteve.getId().toString(), firstPosition)
      .addReorderRequest(holdIlrByJames.getId().toString(), secondPosition)
      .addReorderRequest(recallIlrByCharlotte.getId().toString(), thirdPosition)
      .addReorderRequest(recallTlrByJessica.getId().toString(), fourthPosition)
      .create();

    JsonObject response = requestQueueFixture.reorderQueueForInstance(
      instanceId.toString(), reorderQueueBody);

    verifyQueueUpdatedForInstance(reorderQueueBody, response);
  }


  @ParameterizedTest
  @EnumSource(TlrFeatureStatus.class)
  void logRecordEventIsPublished(TlrFeatureStatus tlrFeatureStatus) {
    reconfigureTlrFeature(tlrFeatureStatus);

    checkOutFixture.checkOutByBarcode(item, rebecca);

    IndividualResource firstHoldRequest = holdRequestForDefaultItem(steve);
    IndividualResource secondHoldRequest = holdRequestForDefaultItem(james);
    IndividualResource firstRecallRequest = recallRequestForDefaultItem(charlotte);
    IndividualResource secondRecallRequest = recallRequestForDefaultItem(jessica);

    JsonObject reorderQueue = new ReorderQueueBuilder()
      .addReorderRequest(firstHoldRequest.getId().toString(), 1)
      .addReorderRequest(secondHoldRequest.getId().toString(), 4)
      .addReorderRequest(firstRecallRequest.getId().toString(), 2)
      .addReorderRequest(secondRecallRequest.getId().toString(), 3)
      .create();

    JsonObject response;
    if (tlrFeatureStatus == TlrFeatureStatus.ENABLED) {
      response = requestQueueFixture.reorderQueueForInstance(item.getInstanceId().toString(),
        reorderQueue);
    }
    else {
      response = requestQueueFixture.reorderQueueForItem(item.getId().toString(),
        reorderQueue);
    }

    verifyQueueUpdatedForItem(reorderQueue, response);

    // TODO: understand why
    int numberOfPublishedEvents = tlrFeatureStatus == TlrFeatureStatus.ENABLED ? 15 : 17;
    final var publishedEvents = Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(FakePubSub::getPublishedEvents, hasSize(numberOfPublishedEvents));

    final var reorderedLogEvents = publishedEvents.filterToList(
      byLogEventType(REQUEST_REORDERED.value()));

    assertThat(reorderedLogEvents, hasSize(1));

    List<JsonObject> reorderedRequests = new JsonObject(JsonObject.mapFrom(reorderedLogEvents.get(0))
      .getString("eventPayload"))
        .getJsonObject("payload")
        .getJsonObject("requests")
        .getJsonArray("reordered")
        .stream()
        .map(o -> (JsonObject) o)
        .collect(toList());

    assertThat(reorderedRequests, hasSize(3));

    reorderedRequests.forEach(r -> {
      assertNotNull(r.getInteger("position"));
      assertNotNull(r.getInteger("previousPosition"));
      assertNotEquals(r.getInteger("position"), r.getInteger("previousPosition"));
    });
  }

  @ParameterizedTest
  @ArgumentsSource(ReorderQueueTestDataSource.class)
  public void canReorderQueueTwice(Integer[] initialState, Integer[] targetState) {
    checkOutFixture.checkOutByBarcode(item, rebecca);

    IndividualResource firstHoldRequest = holdRequestForDefaultItem(steve);
    IndividualResource secondHoldRequest = holdRequestForDefaultItem(james);
    IndividualResource firstRecallRequest = recallRequestForDefaultItem(charlotte);
    IndividualResource secondRecallRequest = recallRequestForDefaultItem(jessica);

    JsonObject initialReorder = new ReorderQueueBuilder()
      .addReorderRequest(firstHoldRequest.getId().toString(), initialState[0])
      .addReorderRequest(secondHoldRequest.getId().toString(), initialState[1])
      .addReorderRequest(firstRecallRequest.getId().toString(), initialState[2])
      .addReorderRequest(secondRecallRequest.getId().toString(), initialState[3])
      .create();

    JsonObject initialReorderResponse = requestQueueFixture
      .reorderQueueForItem(item.getId().toString(), initialReorder);

    verifyQueueUpdatedForItem(initialReorder, initialReorderResponse);

    JsonObject subsequentReorder = new ReorderQueueBuilder()
      .addReorderRequest(firstHoldRequest.getId().toString(), targetState[0])
      .addReorderRequest(secondHoldRequest.getId().toString(), targetState[1])
      .addReorderRequest(firstRecallRequest.getId().toString(), targetState[2])
      .addReorderRequest(secondRecallRequest.getId().toString(), targetState[3])
      .create();

    JsonObject subsequentReorderResponse = requestQueueFixture
      .reorderQueueForItem(item.getId().toString(), subsequentReorder);

    verifyQueueUpdatedForItem(subsequentReorder, subsequentReorderResponse);
  }

  @ParameterizedTest
  @ArgumentsSource(ReorderQueueTestDataSource.class)
  public void canReorderUnifiedQueueTwice(Integer[] initialState, Integer[] targetState) {
    reconfigureTlrFeature(TlrFeatureStatus.ENABLED);

    checkOutFixture.checkOutByBarcode(items.get(0), rebecca);
    checkOutFixture.checkOutByBarcode(items.get(1), rebecca);
    checkOutFixture.checkOutByBarcode(items.get(2), rebecca);

    IndividualResource holdTlrBySteve = holdTitleLevelRequest(steve);
    IndividualResource holdIlrByJames = holdRequestForItem(james, items.get(0));
    IndividualResource recallIlrByCharlotte = recallRequestForItem(charlotte, items.get(1));
    IndividualResource recallTlrByJessica = recallTitleLevelRequest(jessica);

    JsonObject initialReorder = new ReorderQueueBuilder()
      .addReorderRequest(holdTlrBySteve.getId().toString(), initialState[0])
      .addReorderRequest(holdIlrByJames.getId().toString(), initialState[1])
      .addReorderRequest(recallIlrByCharlotte.getId().toString(), initialState[2])
      .addReorderRequest(recallTlrByJessica.getId().toString(), initialState[3])
      .create();

    JsonObject initialReorderResponse = requestQueueFixture
      .reorderQueueForInstance(instanceId.toString(), initialReorder);

    verifyQueueUpdatedForInstance(initialReorder, initialReorderResponse);

    JsonObject subsequentReorder = new ReorderQueueBuilder()
      .addReorderRequest(holdTlrBySteve.getId().toString(), targetState[0])
      .addReorderRequest(holdIlrByJames.getId().toString(), targetState[1])
      .addReorderRequest(recallIlrByCharlotte.getId().toString(), targetState[2])
      .addReorderRequest(recallTlrByJessica.getId().toString(), targetState[3])
      .create();

    JsonObject subsequentReorderResponse = requestQueueFixture
      .reorderQueueForInstance(instanceId.toString(), subsequentReorder);

    verifyQueueUpdatedForInstance(subsequentReorder, subsequentReorderResponse);
  }

  private IndividualResource pageRequestForDefaultItem(IndividualResource requester) {
    return pageRequestForItem(requester, item);
  }

  private IndividualResource pageRequestForItem(IndividualResource requester, ItemResource item) {
    return requestsFixture.place(new RequestBuilder()
      .open()
      .page()
      .forItem(item)
      .by(requester)
      .withPickupServicePoint(servicePointsFixture.cd1()));
  }

  private IndividualResource pageTitleLevelRequestForItem(IndividualResource requester,
    ItemResource item) {

    return requestsFixture.place(new RequestBuilder()
      .titleRequestLevel()
      .open()
      .page()
      .withInstanceId(item.getInstanceId())
      .forItem(item)
      .by(requester)
      .withPickupServicePoint(servicePointsFixture.cd1()));
  }

  private IndividualResource holdRequestForDefaultItem(IndividualResource requester) {
    return holdRequestForItem(requester, item);
  }

  private IndividualResource holdRequestForItem(IndividualResource requester, ItemResource item) {
    return requestsFixture.place(new RequestBuilder()
      .open()
      .hold()
      .forItem(item)
      .by(requester)
      .withPickupServicePoint(servicePointsFixture.cd1()));
  }

  private IndividualResource holdTitleLevelRequest(IndividualResource requester) {
    return requestsFixture.place(new RequestBuilder()
      .titleRequestLevel()
      .open()
      .hold()
      .withInstanceId(instanceId)
      .withItemId(null)
      .by(requester)
      .withPickupServicePoint(servicePointsFixture.cd1()));
  }

  private IndividualResource recallRequestForDefaultItem(IndividualResource requester) {
    return recallRequestForItem(requester, item);
  }

  private IndividualResource recallRequestForItem(IndividualResource requester, ItemResource item) {
    return requestsFixture.place(new RequestBuilder()
      .open()
      .recall()
      .forItem(item)
      .by(requester)
      .withPickupServicePoint(servicePointsFixture.cd1()));
  }

  private IndividualResource recallTitleLevelRequest(IndividualResource requester) {

    return requestsFixture.place(new RequestBuilder()
      .titleRequestLevel()
      .open()
      .recall()
      .withInstanceId(instanceId)
      .withItemId(null)
      .by(requester)
      .withPickupServicePoint(servicePointsFixture.cd1()));
  }

  private IndividualResource inFulfillmentRecallRequestForDefaultItem(
    IndividualResource requester) {

    return inFulfillmentRecallRequestForItem(requester, item);
  }

  private IndividualResource inFulfillmentRecallRequestForItem(
    IndividualResource requester, ItemResource item) {

    return requestsFixture.place(new RequestBuilder()
      .open()
      .recall()
      .withStatus(RequestBuilder.OPEN_IN_TRANSIT)
      .forItem(item)
      .by(requester)
      .withPickupServicePoint(servicePointsFixture.cd1()));
  }

  private IndividualResource inFulfillmentRecallTitleLevelRequestForItem(
    IndividualResource requester, ItemResource item) {

    return requestsFixture.place(new RequestBuilder()
      .titleRequestLevel()
      .open()
      .recall()
      .withStatus(RequestBuilder.OPEN_IN_TRANSIT)
      .forItem(item)
      .withInstanceId(item.getInstanceId())
      .by(requester)
      .withPickupServicePoint(servicePointsFixture.cd1()));
  }

  private void verifyQueueUpdatedForItem(JsonObject initialQueue,
    JsonObject reorderedQueue) {

    verifyQueueUpdated(false, initialQueue, reorderedQueue);
  }

  private void verifyQueueUpdatedForInstance(JsonObject initialQueue,
    JsonObject reorderedQueue) {

    verifyQueueUpdated(true, initialQueue, reorderedQueue);
  }

  private void verifyQueueUpdated(boolean forInstance, JsonObject initialQueue,
    JsonObject reorderedQueue) {

    JsonArray reorderedRequests = reorderedQueue.getJsonArray("requests");

    List<JsonObject> expectedRequests = initialQueue.getJsonArray("reorderedQueue")
      .stream()
      .map(obj -> (JsonObject) obj)
      .sorted(Comparator.comparingInt(request -> request.getInteger("newPosition")))
      .collect(Collectors.toList());

    assertEquals(expectedRequests.size(), reorderedRequests.size(),
      "Expected number of requests and actual do not match");

    assertQueue(expectedRequests, reorderedRequests);

    JsonArray requestsFromDb;
    if (forInstance) {
      requestsFromDb = requestQueueFixture
        .retrieveQueueForInstance(instanceId.toString())
        .getJsonArray("requests");
    }
    else {
      requestsFromDb = requestQueueFixture
        .retrieveQueueForItem(item.getId().toString())
        .getJsonArray("requests");
    }

    assertEquals(reorderedRequests.size(), requestsFromDb.size(),
      "Requests in DB and actual do not match");

    assertQueue(expectedRequests, requestsFromDb);
  }

  private void assertQueue(List<JsonObject> expectedQueue, JsonArray actualQueue) {
    for (int i = 0; i < expectedQueue.size(); i++) {
      JsonObject currentExpectedRequest = expectedQueue.get(i);
      JsonObject currentActualRequest = actualQueue.getJsonObject(i);

      assertEquals(currentExpectedRequest.getString("id"),
        currentActualRequest.getString("id"));
      assertEquals(currentExpectedRequest.getInteger("newPosition"),
        currentActualRequest.getInteger("position"));
    }
  }

  private void verifyValidationFailure(
    Response response, Matcher<String> errorMessageMatcher) {

    assertThat(response.getStatusCode(), is(422));

    JsonArray errors = response.getJson().getJsonArray("errors");
    assertThat(errors.size(), is(1));
    assertThat(errors.getJsonObject(0).getString("message"),
      errorMessageMatcher);
  }
}
