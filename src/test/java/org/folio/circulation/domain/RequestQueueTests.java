package org.folio.circulation.domain;

import static org.folio.circulation.domain.RequestStatus.OPEN_AWAITING_PICKUP;
import static org.folio.circulation.domain.RequestStatus.OPEN_NOT_YET_FILLED;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.vertx.core.json.JsonObject;

class RequestQueueTests {
  private static final String TOP_FULFILLABLE_REQUEST_ID = randomId();

  @ParameterizedTest
  @MethodSource("argumentsForUpdateRequestPositionOnCheckIn")
  void updateRequestPositionOnCheckIn(List<Request> requests, int[] expectedPositions) {
    List<Request> requestsBeforeUpdate = new ArrayList<>(requests);
    assertEquals(requestsBeforeUpdate.size(), expectedPositions.length);

    RequestQueue requestQueue = new RequestQueue(requests);
    requestQueue.updateRequestPositionOnCheckIn(TOP_FULFILLABLE_REQUEST_ID);

    List<Request> requestsAfterUpdate = new ArrayList<>(requestQueue.getRequests());

    assertEquals(requestsAfterUpdate.size(), expectedPositions.length);

    for (int i = 0; i < expectedPositions.length; i++) {
      int expectedPosition = expectedPositions[i];
      Request requestBeforeUpdate = requestsBeforeUpdate.get(i);
      Request requestAfterUpdate = requestsAfterUpdate.get(expectedPosition - 1);
      assertEquals(requestAfterUpdate.getPosition(), expectedPosition);
      assertEquals(requestBeforeUpdate.getId(), requestAfterUpdate.getId());
    }
  }

  @Test
  void replaceRequestShouldHandleRequestsWithNullId() {
    String requestId = randomId();
    List<Request> requests = List.of(
      buildRequest(1, OPEN_NOT_YET_FILLED, requestId));
    RequestQueue requestQueue = new RequestQueue(requests);

    requestQueue.replaceRequest(buildRequest(1, OPEN_NOT_YET_FILLED, null));

    String firstInQueueRequestId = requestQueue.getRequests().stream()
      .findFirst()
      .orElse(null)
      .getId();

    assertEquals(requestId, firstInQueueRequestId);
  }

  private static Stream<Arguments> argumentsForUpdateRequestPositionOnCheckIn() {
    return Stream.of(
      Arguments.of(List.of(
          buildRequest(1, OPEN_NOT_YET_FILLED, TOP_FULFILLABLE_REQUEST_ID)),
        new int[] { 1 }),

      Arguments.of(List.of(
          buildRequest(1, OPEN_NOT_YET_FILLED, TOP_FULFILLABLE_REQUEST_ID),
          buildRequest(2, OPEN_NOT_YET_FILLED, randomId())),
        new int[] { 1, 2 }),

      Arguments.of(List.of(
          buildRequest(1, OPEN_AWAITING_PICKUP, randomId()),
          buildRequest(2, OPEN_AWAITING_PICKUP, randomId()),
          buildRequest(3, OPEN_NOT_YET_FILLED, TOP_FULFILLABLE_REQUEST_ID)),
        new int[] { 1, 2, 3 }),

      Arguments.of(List.of(
          buildRequest(1, OPEN_NOT_YET_FILLED, randomId()),
          buildRequest(2, OPEN_NOT_YET_FILLED, TOP_FULFILLABLE_REQUEST_ID),
          buildRequest(3, OPEN_NOT_YET_FILLED, randomId())),
        new int[] { 2, 1, 3 }),

      Arguments.of(List.of(
          buildRequest(1, OPEN_NOT_YET_FILLED, randomId()),
          buildRequest(2, OPEN_NOT_YET_FILLED, randomId()),
          buildRequest(3, OPEN_NOT_YET_FILLED, TOP_FULFILLABLE_REQUEST_ID)),
        new int[] { 2, 3, 1 }),

      Arguments.of(List.of(
          buildRequest(1, OPEN_AWAITING_PICKUP, randomId()),
          buildRequest(2, OPEN_NOT_YET_FILLED, randomId()),
          buildRequest(3, OPEN_NOT_YET_FILLED, TOP_FULFILLABLE_REQUEST_ID)),
        new int[] { 1, 3, 2 })
    );
  }

  private static Request buildRequest(int position, RequestStatus status, String requestId) {
    JsonObject json = new JsonObject()
      .put("id", requestId)
      .put("status", status.getValue())
      .put("position", position);

    return new Request(null, null, json, null, null, null, null, null, null, null, null, null,
      null, false, null, false, null);
  }

  private static String randomId() {
    return UUID.randomUUID().toString();
  }
}
