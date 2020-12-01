package org.folio.circulation.domain;

import static org.folio.circulation.domain.RequestQueue.requestQueueOf;
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

    final var requestQueue = requestQueueOf(onlyRequest);

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

    final var first = requestAtPosition(itemId, 1);
    final var second = requestAtPosition(itemId, 2);
    final var third = requestAtPosition(itemId, 3);

    final var requestQueue = requestQueueOf(first, second, third);

    requestQueue.remove(third);

    assertThat(requestQueue, hasSize(2));
    assertThat(requestQueue, doesNotInclude(third));
    assertThat(requestQueue, includes(first));
    assertThat(requestQueue, includes(second));

    assertThat("Removed request should not have a position",
      third.getPosition(), is(nullValue()));

    assertThat("First request should still in correct position",
      first.getPosition(), is(1));

    assertThat("Second request should still in correct position",
      second.getPosition(), is(2));

    assertThat("No requests have changed position",
      requestQueue.getRequestsWithChangedPosition(), is(empty()));
  }

  @Test
  void canRemoveFirstRequestInQueue() {
    final UUID itemId = UUID.randomUUID();

    final var first = requestAtPosition(itemId, 1);
    final var second = requestAtPosition(itemId, 2);
    final var third = requestAtPosition(itemId, 3);

    final var requestQueue = requestQueueOf(first, second, third);

    requestQueue.remove(first);

    assertThat(requestQueue, hasSize(2));
    assertThat(requestQueue, doesNotInclude(first));
    assertThat(requestQueue, includes(second));
    assertThat(requestQueue, includes(third));

    assertThat("Removed request should not have a position",
      first.getPosition(), is(nullValue()));

    assertThat("Second request should have moved up the queue",
      second.getPosition(), is(1));

    assertThat("Third request should have moved up the queue",
      third.getPosition(), is(2));

    assertThat("Second and third requests have changed position",
      requestQueue.getRequestsWithChangedPosition(), contains(third, second));
  }

  @Test
  void canRemoveMiddleRequestInQueue() {
    final UUID itemId = UUID.randomUUID();

    Request first = requestAtPosition(itemId, 1);
    Request second = requestAtPosition(itemId, 2);
    Request third = requestAtPosition(itemId, 3);
    Request fourth = requestAtPosition(itemId, 4);

    final var requestQueue = requestQueueOf(first, second, third, fourth);

    requestQueue.remove(second);

    assertThat(requestQueue, hasSize(3));
    assertThat(requestQueue, doesNotInclude(second));
    assertThat(requestQueue, includes(first));
    assertThat(requestQueue, includes(third));
    assertThat(requestQueue, includes(fourth));

    assertThat("Removed request should not have a position",
      second.getPosition(), is(nullValue()));

    assertThat("First request should be at the same position",
      first.getPosition(), is(1));

    assertThat("Third request should have moved up the queue",
      third.getPosition(), is(2));

    assertThat("Fourth request should have moved up the queue",
      fourth.getPosition(), is(3));

    assertThat("Second and third requests have changed position",
      requestQueue.getRequestsWithChangedPosition(), contains(fourth, third));
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
