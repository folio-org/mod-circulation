package org.folio.circulation.resources;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import api.support.APITests;
import api.support.builders.ReorderQueueBuilder;
import api.support.builders.RequestBuilder;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
public class RequestQueueResourceTest extends APITests {

  private IndividualResource item;
  private IndividualResource jessica;
  private IndividualResource steve;
  private IndividualResource james;
  private IndividualResource rebecca;
  private IndividualResource charlotte;

  @Before
  public void setUp() throws Exception {
    item = itemsFixture.basedUponSmallAngryPlanet();

    jessica = usersFixture.jessica();
    steve = usersFixture.steve();
    james = usersFixture.james();
    rebecca = usersFixture.rebecca();
    charlotte = usersFixture.charlotte();
  }

  @Test
  public void validationErrorOnItemDoNotExists() throws Exception {
    Response response = requestQueueFixture.attemptReorderQueue(
      UUID.randomUUID().toString(),
      new ReorderQueueBuilder()
        .addReorderRequest(UUID.randomUUID().toString(), 1)
        .create()
    );

    assertThat(response.getStatusCode(), is(404));
    assertTrue(response.getBody()
      .matches("Item record with ID .+ cannot be found"));
  }

  @Test
  public void validationErrorOnPageRequestDisplace() throws Exception {
    IndividualResource pageRequest = pageRequest(steve);
    IndividualResource recallRequest = recallRequest(jessica);

    Response response = requestQueueFixture.attemptReorderQueue(
      item.getId().toString(),
      new ReorderQueueBuilder()
        .addReorderRequest(recallRequest.getId().toString(), 1)
        .addReorderRequest(pageRequest.getId().toString(), 2)
        .create()
    );

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(),
      is("Page requests can not be displaced from position 1."));
  }

  @Test
  public void validationErrorOnFulfillingRequestDisplace() throws Exception {
    loansFixture.checkOutByBarcode(item, usersFixture.rebecca());

    IndividualResource inFulfillmentRequest = inFulfillmentRecallRequest(steve);
    IndividualResource recallRequest = recallRequest(jessica);

    Response response = requestQueueFixture.attemptReorderQueue(
      item.getId().toString(),
      new ReorderQueueBuilder()
        .addReorderRequest(recallRequest.getId().toString(), 1)
        .addReorderRequest(inFulfillmentRequest.getId().toString(), 2)
        .create()
    );

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(),
      is("Requests can not be displaced from position 1 when fulfillment begun."));
  }

  @Test
  public void validationErrorWhenNonExistingRequestProvidedInReorderedQueue() throws Exception {
    loansFixture.checkOutByBarcode(item, usersFixture.rebecca());

    IndividualResource firstRecallRequest = recallRequest(steve);
    IndividualResource secondRecallRequest = recallRequest(jessica);

    Response response = requestQueueFixture.attemptReorderQueue(
      item.getId().toString(),
      new ReorderQueueBuilder()
        .addReorderRequest(secondRecallRequest.getId().toString(), 1)
        .addReorderRequest(firstRecallRequest.getId().toString(), 2)
        .addReorderRequest(UUID.randomUUID().toString(), 3)
        .create()
    );

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(),
      is("There is inconsistency between provided reordered queue and item queue."));
  }

  @Test
  public void validationErrorWhenNotAllRequestsProvidedInReorderedQueue() throws Exception {
    loansFixture.checkOutByBarcode(item, usersFixture.rebecca());

    holdRequest(steve);
    IndividualResource secondRecallRequest = recallRequest(jessica);

    Response response = requestQueueFixture.attemptReorderQueue(
      item.getId().toString(),
      new ReorderQueueBuilder()
        .addReorderRequest(secondRecallRequest.getId().toString(), 1)
        .create()
    );

    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(),
      is("There is inconsistency between provided reordered queue and item queue."));
  }

  @Test
  public void canNotSaveRequestsWithDuplicatedPositions() throws Exception {
    loansFixture.checkOutByBarcode(item, usersFixture.rebecca());

    IndividualResource holdRequest = holdRequest(steve);
    IndividualResource recallRequest = recallRequest(jessica);

    Response response = requestQueueFixture.attemptReorderQueue(
      item.getId().toString(),
      new ReorderQueueBuilder()
        .addReorderRequest(recallRequest.getId().toString(), 1)
        .addReorderRequest(holdRequest.getId().toString(), 1)
        .create()
    );

    assertThat(response.getStatusCode(), is(500));
    assertThat(response.getBody(),
      is("Some requests have not been updated. Fetch the queue and try again."));
  }

  @Test
  @Parameters({
    "1, 2, 3, 4",
    "1, 2, 4, 3",
    "2, 1, 3, 4",
    "2, 1, 4, 3",
    "4, 3, 2, 1",
    "4, 3, 1, 2",
    "3, 4, 2, 1",
    "3, 4, 1, 2",
    "3, 4, 1, 50",
    "40, 2, 1, 3",
  })
  public void shouldReorderQueueSuccessfully(int firstPosition, int secondPosition,
                                             int thirdPosition, int fourthPosition) throws Exception {
    loansFixture.checkOutByBarcode(item, rebecca);

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

    assertQueue(reorderQueue, response);
  }

  @Test
  @Parameters(source = ReorderQueueTestDataSource.class)
  public void canReorderQueueTwice(Integer[] initialState, Integer[] targetState) throws Exception {
    loansFixture.checkOutByBarcode(item, rebecca);

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

    assertQueue(initialReorder, initialReorderResponse);

    JsonObject subsequentReorder = new ReorderQueueBuilder()
      .addReorderRequest(firstHoldRequest.getId().toString(), targetState[0])
      .addReorderRequest(secondHoldRequest.getId().toString(), targetState[1])
      .addReorderRequest(firstRecallRequest.getId().toString(), targetState[2])
      .addReorderRequest(secondRecallRequest.getId().toString(), targetState[3])
      .create();

    JsonObject subsequentReorderResponse = requestQueueFixture
      .reorderQueue(item.getId().toString(), subsequentReorder);

    assertQueue(subsequentReorder, subsequentReorderResponse);
  }

  private IndividualResource pageRequest(IndividualResource requester) throws Exception {
    return requestsFixture.place(
      new RequestBuilder()
        .open()
        .page()
        .forItem(item)
        .by(requester)
        .withPickupServicePoint(servicePointsFixture.cd1())
    );
  }

  private IndividualResource holdRequest(IndividualResource requester) throws Exception {
    return requestsFixture.place(
      new RequestBuilder()
        .open()
        .hold()
        .forItem(item)
        .by(requester)
        .withPickupServicePoint(servicePointsFixture.cd1())
    );
  }

  private IndividualResource recallRequest(IndividualResource requester) throws Exception {
    return requestsFixture.place(
      new RequestBuilder()
        .open()
        .recall()
        .forItem(item)
        .by(requester)
        .withPickupServicePoint(servicePointsFixture.cd1())
    );
  }

  private IndividualResource inFulfillmentRecallRequest(IndividualResource requester) throws Exception {
    return requestsFixture.place(
      new RequestBuilder()
        .open()
        .recall()
        .withStatus(RequestBuilder.OPEN_IN_TRANSIT)
        .forItem(item)
        .by(requester)
        .withPickupServicePoint(servicePointsFixture.cd1())
    );
  }

  private void assertQueue(JsonObject initialQueue, JsonObject reorderedQueue)
    throws Exception {

    JsonArray reorderedRequests = reorderedQueue.getJsonArray("requests");
    List<JsonObject> expectedRequests = initialQueue.getJsonArray("reorderedQueue")
      .stream()
      .map(obj -> (JsonObject) obj)
      .sorted(Comparator.comparingInt(request -> request.getInteger("position")))
      .collect(Collectors.toList());

    assertEquals("Expected number of requests and actual do not match",
      expectedRequests.size(), reorderedRequests.size());

    for (int i = 0; i < expectedRequests.size(); i++) {
      JsonObject initialCurrentRequest = expectedRequests.get(i);
      JsonObject currentRequest = reorderedRequests.getJsonObject(i);

      assertThat(currentRequest.getString("id"),
        is(initialCurrentRequest.getString("id")));
      assertThat(currentRequest.getInteger("position"),
        is(initialCurrentRequest.getInteger("position")));
    }

    JsonArray requestsInDb = requestQueueFixture
      .retrieveQueue(item.getId().toString())
      .getJsonArray("requests");

    assertEquals("Requests in DB and actual do not match",
      reorderedRequests.size(), requestsInDb.size());

    for (int i = 0; i < requestsInDb.size(); i++) {
      JsonObject currentRequest = reorderedRequests.getJsonObject(i);
      JsonObject currentRequestFromDb = requestsInDb.getJsonObject(i);

      assertEquals(currentRequest.getString("id"),
        currentRequestFromDb.getString("id"));
      assertEquals(currentRequest.getInteger("position"),
        currentRequestFromDb.getInteger("position"));
    }
  }
}
