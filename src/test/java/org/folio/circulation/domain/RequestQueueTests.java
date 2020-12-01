package org.folio.circulation.domain;

import static org.folio.circulation.domain.RequestQueue.requestQueueOf;
import static org.folio.circulation.matchers.requests.RequestMatchers.hasNoPosition;
import static org.folio.circulation.matchers.requests.RequestMatchers.inPosition;
import static org.folio.circulation.matchers.requests.RequestQueueMatchers.doesNotInclude;
import static org.folio.circulation.matchers.requests.RequestQueueMatchers.hasSize;
import static org.folio.circulation.matchers.requests.RequestQueueMatchers.includes;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import api.support.builders.RequestBuilder;

class RequestQueueTests {
  @Test
  void canRemoveOnlyRequestInQueue() {
    final var itemId = UUID.randomUUID();

    final var onlyRequest = requestAtPosition(itemId, 1);

    final var queue = requestQueueOf(onlyRequest);

    queue.remove(onlyRequest);

    assertThat(queue, hasSize(0));

    assertThat(queue, doesNotInclude(onlyRequest));

    assertThat(onlyRequest, hasNoPosition());

    assertThat(queue.getRequestsWithChangedPosition(), is(empty()));
  }

  @Test
  void canRemoveLastRequestInQueue() {
    final var itemId = UUID.randomUUID();

    final var firstRequest = requestAtPosition(itemId, 1);
    final var secondRequest = requestAtPosition(itemId, 2);
    final var thirdRequest = requestAtPosition(itemId, 3);

    final var queue = requestQueueOf(firstRequest, secondRequest, thirdRequest);

    queue.remove(thirdRequest);

    assertThat(queue, hasSize(2));

    assertThat(queue, includes(firstRequest));
    assertThat(queue, includes(secondRequest));
    assertThat(queue, doesNotInclude(thirdRequest));

    assertThat(firstRequest, is(inPosition(1)));
    assertThat(secondRequest, is(inPosition(2)));
    assertThat(thirdRequest, hasNoPosition());

    assertThat(queue.getRequestsWithChangedPosition(), is(empty()));
  }

  @Test
  void canRemoveFirstRequestInQueue() {
    final var itemId = UUID.randomUUID();

    final var firstRequest = requestAtPosition(itemId, 1);
    final var secondRequest = requestAtPosition(itemId, 2);
    final var thirdRequest = requestAtPosition(itemId, 3);

    final var queue = requestQueueOf(firstRequest, secondRequest, thirdRequest);

    queue.remove(firstRequest);

    assertThat(queue, hasSize(2));

    assertThat(queue, doesNotInclude(firstRequest));
    assertThat(queue, includes(secondRequest));
    assertThat(queue, includes(thirdRequest));

    assertThat(firstRequest, hasNoPosition());
    assertThat(secondRequest, is(inPosition(1)));
    assertThat(thirdRequest, is(inPosition(2)));

    assertThat(queue.getRequestsWithChangedPosition(), contains(thirdRequest, secondRequest));
  }

  @Test
  void canRemoveMiddleRequestInQueue() {
    final var itemId = UUID.randomUUID();

    final var firstRequest = requestAtPosition(itemId, 1);
    final var secondRequest = requestAtPosition(itemId, 2);
    final var thirdRequest = requestAtPosition(itemId, 3);
    final var fourthRequest = requestAtPosition(itemId, 4);

    final var queue = requestQueueOf(firstRequest, secondRequest, thirdRequest, fourthRequest);

    queue.remove(secondRequest);

    assertThat(queue, hasSize(3));

    assertThat(queue, includes(firstRequest));
    assertThat(queue, doesNotInclude(secondRequest));
    assertThat(queue, includes(thirdRequest));
    assertThat(queue, includes(fourthRequest));

    assertThat(firstRequest, is(inPosition(1)));
    assertThat(secondRequest, hasNoPosition());
    assertThat(thirdRequest, is(inPosition(2)));
    assertThat(fourthRequest, is(inPosition(3)));

    assertThat(queue.getRequestsWithChangedPosition(), contains(fourthRequest, thirdRequest));
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
