package api.queue;

import static api.support.fakes.PublishedEvents.byLogEventType;
import static java.util.stream.Collectors.toList;
import static org.folio.circulation.domain.representations.logs.LogEventType.REQUEST_REORDERED;
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

import api.support.APITests;
import api.support.builders.ReorderQueueBuilder;
import api.support.builders.RequestBuilder;
import api.support.fakes.FakePubSub;
import api.support.http.IndividualResource;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class RequestQueueResourceTest extends APITests {
  private IndividualResource item;
  private IndividualResource jessica;
  private IndividualResource steve;
  private IndividualResource james;
  private IndividualResource rebecca;
  private IndividualResource charlotte;

  @BeforeEach
  public void setUp() {
    item = itemsFixture.basedUponSmallAngryPlanet();

    jessica = usersFixture.jessica();
    steve = usersFixture.steve();
    james = usersFixture.james();
    rebecca = usersFixture.rebecca();
    charlotte = usersFixture.charlotte();
  }

  @Test
  void validationErrorOnItemDoNotExists() {
    Response response = requestQueueFixture.attemptReorderQueue(
      UUID.randomUUID().toString(),
      new ReorderQueueBuilder()
        .addReorderRequest(UUID.randomUUID().toString(), 1)
        .create());

    assertThat(response.getStatusCode(), is(404));
    assertTrue(response.getBody()
      .matches("Item record with ID .+ cannot be found"));
  }

  @Test
  void refuseAttemptToMovePageRequestFromFirstPosition() {
    IndividualResource pageRequest = pageRequest(steve);
    IndividualResource recallRequest = recallRequest(jessica);

    Response response = requestQueueFixture.attemptReorderQueue(
      item.getId().toString(),
      new ReorderQueueBuilder()
        .addReorderRequest(recallRequest.getId().toString(), 1)
        .addReorderRequest(pageRequest.getId().toString(), 2)
        .create());

    verifyValidationFailure(response,
      is("Page requests can not be displaced from position 1."));
  }

  @Test
  void refuseAttemptToMoveRequestBeingFulfilledFromFirstPosition() {
    checkOutFixture.checkOutByBarcode(item, usersFixture.rebecca());

    IndividualResource inFulfillmentRequest = inFulfillmentRecallRequest(steve);
    IndividualResource recallRequest = recallRequest(jessica);

    Response response = requestQueueFixture.attemptReorderQueue(
      item.getId().toString(),
      new ReorderQueueBuilder()
        .addReorderRequest(recallRequest.getId().toString(), 1)
        .addReorderRequest(inFulfillmentRequest.getId().toString(), 2)
        .create());

    verifyValidationFailure(response,
      is("Requests can not be displaced from position 1 when fulfillment begun."));
  }

  @Test
  void refuseAttemptToTryingToAddRequestToQueueDuringReorder() {
    checkOutFixture.checkOutByBarcode(item, usersFixture.rebecca());

    IndividualResource firstRecallRequest = recallRequest(steve);
    IndividualResource secondRecallRequest = recallRequest(jessica);

    Response response = requestQueueFixture.attemptReorderQueue(
      item.getId().toString(),
      new ReorderQueueBuilder()
        .addReorderRequest(secondRecallRequest.getId().toString(), 1)
        .addReorderRequest(firstRecallRequest.getId().toString(), 2)
        .addReorderRequest(UUID.randomUUID().toString(), 3)
        .create());

    verifyValidationFailure(response,
      is("There is inconsistency between provided reordered queue and item queue."));
  }

  @Test
  void refuseWhenNotAllRequestsProvidedInReorderedQueue() {
    checkOutFixture.checkOutByBarcode(item, usersFixture.rebecca());

    holdRequest(steve);

    IndividualResource secondRecallRequest = recallRequest(jessica);

    Response response = requestQueueFixture.attemptReorderQueue(
      item.getId().toString(),
      new ReorderQueueBuilder()
        .addReorderRequest(secondRecallRequest.getId().toString(), 1)
        .create());

    verifyValidationFailure(response,
      is("There is inconsistency between provided reordered queue and item queue."));
  }

  @ParameterizedTest
  @CsvSource(value = {
    "1, 2, 5, 4",
    "0, 2, 3, 4",
    "6, 2, 3, 1",
    "70, 0, 10, 2",
    "1, 3, 4, 5",
    "1, 2, 4, 5",
    "1, 2, 3, 5",
  })
  void refuseWhenPositionsAreNotSequential(int firstPosition,
    int secondPosition, int thirdPosition, int fourthPosition) {

    checkOutFixture.checkOutByBarcode(item, rebecca);

    IndividualResource firstHoldRequest = holdRequest(steve);
    IndividualResource secondHoldRequest = holdRequest(james);
    IndividualResource firstRecallRequest = recallRequest(charlotte);
    IndividualResource secondRecallRequest = recallRequest(jessica);

    JsonObject reorderQueue = new ReorderQueueBuilder()
      .addReorderRequest(firstHoldRequest.getId().toString(), firstPosition)
      .addReorderRequest(secondHoldRequest.getId().toString(), secondPosition)
      .addReorderRequest(firstRecallRequest.getId().toString(), thirdPosition)
      .addReorderRequest(secondRecallRequest.getId().toString(), fourthPosition)
      .create();

    Response response = requestQueueFixture.attemptReorderQueue(
      item.getId().toString(), reorderQueue);

    verifyValidationFailure(response, is("Positions must have sequential order."));
  }

  @Test
  void refuseAttemptToReorderRequestsWithDuplicatedPositions() {
    checkOutFixture.checkOutByBarcode(item, usersFixture.rebecca());

    IndividualResource holdRequest = holdRequest(steve);
    IndividualResource recallRequest = recallRequest(jessica);

    Response response = requestQueueFixture.attemptReorderQueue(
      item.getId().toString(),
      new ReorderQueueBuilder()
        .addReorderRequest(recallRequest.getId().toString(), 1)
        .addReorderRequest(holdRequest.getId().toString(), 1)
        .create());

    verifyValidationFailure(response, is("Positions must have sequential order."));
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
    "3, 4, 1, 2",
  })
  void shouldReorderQueueSuccessfully(int firstPosition,
    int secondPosition, int thirdPosition, int fourthPosition) {

    checkOutFixture.checkOutByBarcode(item, rebecca);

    IndividualResource firstHoldRequest = holdRequest(steve);
    IndividualResource secondHoldRequest = holdRequest(james);
    IndividualResource firstRecallRequest = recallRequest(charlotte);
    IndividualResource secondRecallRequest = recallRequest(jessica);

    JsonObject reorderQueue = new ReorderQueueBuilder()
      .addReorderRequest(firstHoldRequest.getId().toString(), firstPosition)
      .addReorderRequest(secondHoldRequest.getId().toString(), secondPosition)
      .addReorderRequest(firstRecallRequest.getId().toString(), thirdPosition)
      .addReorderRequest(secondRecallRequest.getId().toString(), fourthPosition)
      .create();

    JsonObject response = requestQueueFixture
      .reorderQueue(item.getId().toString(), reorderQueue);

    verifyQueueUpdated(reorderQueue, response);
  }

  @Test
  void logRecordEventIsPublished() {
    checkOutFixture.checkOutByBarcode(item, rebecca);

    IndividualResource firstHoldRequest = holdRequest(steve);
    IndividualResource secondHoldRequest = holdRequest(james);
    IndividualResource firstRecallRequest = recallRequest(charlotte);
    IndividualResource secondRecallRequest = recallRequest(jessica);

    JsonObject reorderQueue = new ReorderQueueBuilder()
      .addReorderRequest(firstHoldRequest.getId().toString(), 1)
      .addReorderRequest(secondHoldRequest.getId().toString(), 4)
      .addReorderRequest(firstRecallRequest.getId().toString(), 2)
      .addReorderRequest(secondRecallRequest.getId().toString(), 3)
      .create();

    JsonObject response = requestQueueFixture.reorderQueue(item.getId()
      .toString(), reorderQueue);

    verifyQueueUpdated(reorderQueue, response);

    final var publishedEvents = Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(FakePubSub::getPublishedEvents, hasSize(17));

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

    IndividualResource firstHoldRequest = holdRequest(steve);
    IndividualResource secondHoldRequest = holdRequest(james);
    IndividualResource firstRecallRequest = recallRequest(charlotte);
    IndividualResource secondRecallRequest = recallRequest(jessica);

    JsonObject initialReorder = new ReorderQueueBuilder()
      .addReorderRequest(firstHoldRequest.getId().toString(), initialState[0])
      .addReorderRequest(secondHoldRequest.getId().toString(), initialState[1])
      .addReorderRequest(firstRecallRequest.getId().toString(), initialState[2])
      .addReorderRequest(secondRecallRequest.getId().toString(), initialState[3])
      .create();

    JsonObject initialReorderResponse = requestQueueFixture
      .reorderQueue(item.getId().toString(), initialReorder);

    verifyQueueUpdated(initialReorder, initialReorderResponse);

    JsonObject subsequentReorder = new ReorderQueueBuilder()
      .addReorderRequest(firstHoldRequest.getId().toString(), targetState[0])
      .addReorderRequest(secondHoldRequest.getId().toString(), targetState[1])
      .addReorderRequest(firstRecallRequest.getId().toString(), targetState[2])
      .addReorderRequest(secondRecallRequest.getId().toString(), targetState[3])
      .create();

    JsonObject subsequentReorderResponse = requestQueueFixture
      .reorderQueue(item.getId().toString(), subsequentReorder);

    verifyQueueUpdated(subsequentReorder, subsequentReorderResponse);
  }

  private IndividualResource pageRequest(IndividualResource requester) {
    return requestsFixture.place(new RequestBuilder()
      .open()
      .page()
      .forItem(item)
      .by(requester)
      .withPickupServicePoint(servicePointsFixture.cd1()));
  }

  private IndividualResource holdRequest(IndividualResource requester) {
    return requestsFixture.place(new RequestBuilder()
      .open()
      .hold()
      .forItem(item)
      .by(requester)
      .withPickupServicePoint(servicePointsFixture.cd1()));
  }

  private IndividualResource recallRequest(IndividualResource requester) {
    return requestsFixture.place(new RequestBuilder()
      .open()
      .recall()
      .forItem(item)
      .by(requester)
      .withPickupServicePoint(servicePointsFixture.cd1()));
  }

  private IndividualResource inFulfillmentRecallRequest(
    IndividualResource requester) {

    return requestsFixture.place(new RequestBuilder()
      .open()
      .recall()
      .withStatus(RequestBuilder.OPEN_IN_TRANSIT)
      .forItem(item)
      .by(requester)
      .withPickupServicePoint(servicePointsFixture.cd1()));
  }

  private void verifyQueueUpdated(JsonObject initialQueue,
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

    JsonArray requestsFromDb = requestQueueFixture
      .retrieveQueue(item.getId().toString())
      .getJsonArray("requests");

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
