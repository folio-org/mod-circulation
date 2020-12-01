package org.folio.circulation.domain;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.folio.circulation.matchers.requests.RequestQueueMatchers.doesNotInclude;
import static org.folio.circulation.matchers.requests.RequestQueueMatchers.hasSize;
import static org.folio.circulation.matchers.requests.RequestQueueMatchers.includes;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import api.support.builders.RequestBuilder;

class RequestQueueTests {
  @Test
  void canRemoveOnlyRequestInQueue() {
    final UUID itemId = UUID.randomUUID();

    Request onlyRequest = requestAtPosition(itemId, 1);

    final RequestQueue requestQueue = new RequestQueue(singletonList(onlyRequest));

    requestQueue.remove(onlyRequest);

    assertThat(requestQueue, hasSize(0));

    assertThat(requestQueue, doesNotInclude(onlyRequest));

    assertThat("Removed request should not have a position",
      onlyRequest.getPosition(), is(nullValue()));

    assertThat("No requests have changed position",
      requestQueue.getRequestsWithChangedPosition(), is(empty()));
  }

  @Test
  void canRemoveLastRequestInQueue() {
    final UUID itemId = UUID.randomUUID();

    Request firstRequest = requestAtPosition(itemId, 1);
    Request secondRequest = requestAtPosition(itemId, 2);
    Request thirdRequest = requestAtPosition(itemId, 3);

    final RequestQueue requestQueue = new RequestQueue(
      asList(firstRequest, secondRequest, thirdRequest));

    requestQueue.remove(thirdRequest);

    assertThat(requestQueue, hasSize(2));
    assertThat(requestQueue, doesNotInclude(thirdRequest));
    assertThat(requestQueue, includes(firstRequest));
    assertThat(requestQueue, includes(secondRequest));

    assertThat("Removed request should not have a position",
      thirdRequest.getPosition(), is(nullValue()));

    assertThat("First request should still in correct position",
      firstRequest.getPosition(), is(1));

    assertThat("Second request should still in correct position",
      secondRequest.getPosition(), is(2));

    assertThat("No requests have changed position",
      requestQueue.getRequestsWithChangedPosition(), is(empty()));
  }

  @Test
  void canRemoveFirstRequestInQueue() {
    final UUID itemId = UUID.randomUUID();

    Request firstRequest = requestAtPosition(itemId, 1);
    Request secondRequest = requestAtPosition(itemId, 2);
    Request thirdRequest = requestAtPosition(itemId, 3);

    final RequestQueue requestQueue = new RequestQueue(
      asList(firstRequest, secondRequest, thirdRequest));

    requestQueue.remove(firstRequest);

    assertThat(requestQueue, hasSize(2));
    assertThat(requestQueue, doesNotInclude(firstRequest));
    assertThat(requestQueue, includes(secondRequest));
    assertThat(requestQueue, includes(thirdRequest));

    assertThat("Removed request should not have a position",
      firstRequest.getPosition(), is(nullValue()));

    assertThat("Second request should have moved up the queue",
      secondRequest.getPosition(), is(1));

    assertThat("Third request should have moved up the queue",
      thirdRequest.getPosition(), is(2));

    assertThat("Second and third requests have changed position",
      requestQueue.getRequestsWithChangedPosition(), contains(thirdRequest, secondRequest));
  }

  @Test
  void canRemoveMiddleRequestInQueue() {
    final UUID itemId = UUID.randomUUID();

    Request firstRequest = requestAtPosition(itemId, 1);
    Request secondRequest = requestAtPosition(itemId, 2);
    Request thirdRequest = requestAtPosition(itemId, 3);
    Request fourthRequest = requestAtPosition(itemId, 4);

    final RequestQueue requestQueue = new RequestQueue(
      asList(firstRequest, secondRequest, thirdRequest, fourthRequest));

    requestQueue.remove(secondRequest);

    assertThat(requestQueue, hasSize(3));
    assertThat(requestQueue, doesNotInclude(secondRequest));
    assertThat(requestQueue, includes(firstRequest));
    assertThat(requestQueue, includes(thirdRequest));
    assertThat(requestQueue, includes(fourthRequest));

    assertThat("Removed request should not have a position",
      secondRequest.getPosition(), is(nullValue()));

    assertThat("First request should be at the same position",
      firstRequest.getPosition(), is(1));

    assertThat("Third request should have moved up the queue",
      thirdRequest.getPosition(), is(2));

    assertThat("Fourth request should have moved up the queue",
      fourthRequest.getPosition(), is(3));

    assertThat("Second and third requests have changed position",
      requestQueue.getRequestsWithChangedPosition(), contains(fourthRequest, thirdRequest));
  }

  private Request requestAtPosition(UUID itemId, Integer position) {
    return Request.from(new RequestBuilder()
      .withId(UUID.randomUUID())
      .open()
      .hold()
      .withItemId(itemId)
      .withPosition(position)
      .create());
  }
}
