package org.folio.circulation.domain;

import api.support.builders.RequestBuilder;
import org.junit.Test;

import java.util.UUID;

import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class RequestQueueTests {

  @Test
  public void canRemoveOnlyRequestInQueue() {
    final UUID itemId = UUID.randomUUID();

    Request onlyRequest = requestAtPosition(itemId, 1);

    final RequestQueue requestQueue = new RequestQueue(asList(onlyRequest));

    requestQueue.remove(onlyRequest);

    assertThat("Should have no requests", requestQueue.size(), is(0));

    assertThat("Should not contain removed request",
      requestQueue.contains(onlyRequest), is(false));

    assertThat("Removed request should not have a position",
      onlyRequest.getPosition(), is(nullValue()));
  }

  @Test
  public void canRemoveLastRequestInQueue() {
    final UUID itemId = UUID.randomUUID();

    Request firstRequest = requestAtPosition(itemId, 1);
    Request secondRequest = requestAtPosition(itemId, 2);
    Request thirdRequest = requestAtPosition(itemId, 3);

    final RequestQueue requestQueue = new RequestQueue(
      asList(firstRequest, secondRequest, thirdRequest));

    requestQueue.remove(thirdRequest);

    assertThat("Should have two requests", requestQueue.size(), is(2));

    assertThat("Should not contain removed request",
      requestQueue.contains(thirdRequest), is(false));

    assertThat("Removed request should not have a position",
      thirdRequest.getPosition(), is(nullValue()));

    assertThat("Should contain first request",
      requestQueue.contains(firstRequest), is(true));

    assertThat("Should contain second request",
      requestQueue.contains(secondRequest), is(true));


    assertThat("First request should still in correct position",
      firstRequest.getPosition(), is(1));

    assertThat("Second request should still in correct position",
      secondRequest.getPosition(), is(2));
  }

  @Test
  public void canRemoveFirstRequestInQueue() {
    final UUID itemId = UUID.randomUUID();

    Request firstRequest = requestAtPosition(itemId, 1);
    Request secondRequest = requestAtPosition(itemId, 2);
    Request thirdRequest = requestAtPosition(itemId, 3);

    final RequestQueue requestQueue = new RequestQueue(
      asList(firstRequest, secondRequest, thirdRequest));

    requestQueue.remove(firstRequest);

    assertThat("Should have two requests", requestQueue.size(), is(2));

    assertThat("Should not contain removed request",
      requestQueue.contains(firstRequest), is(false));

    assertThat("Removed request should not have a position",
      firstRequest.getPosition(), is(nullValue()));

    assertThat("Should contain second request",
      requestQueue.contains(secondRequest), is(true));

    assertThat("Should contain third request",
      requestQueue.contains(thirdRequest), is(true));


    assertThat("Second request should have moved up the queue",
      secondRequest.getPosition(), is(1));

    assertThat("Third request should have moved up the queue",
      thirdRequest.getPosition(), is(2));
  }

  @Test
  public void canRemoveMiddleRequestInQueue() {
    final UUID itemId = UUID.randomUUID();

    Request firstRequest = requestAtPosition(itemId, 1);
    Request secondRequest = requestAtPosition(itemId, 2);
    Request thirdRequest = requestAtPosition(itemId, 3);
    Request fourthRequest = requestAtPosition(itemId, 4);

    final RequestQueue requestQueue = new RequestQueue(
      asList(firstRequest, secondRequest, thirdRequest, fourthRequest));

    requestQueue.remove(secondRequest);

    assertThat("Should have three requests", requestQueue.size(), is(3));

    assertThat("Should not contain removed request",
      requestQueue.contains(secondRequest), is(false));

    assertThat("Removed request should not have a position",
      secondRequest.getPosition(), is(nullValue()));

    assertThat("Should contain first request",
      requestQueue.contains(firstRequest), is(true));

    assertThat("Should contain third request",
      requestQueue.contains(thirdRequest), is(true));

    assertThat("Should contain fourth request",
      requestQueue.contains(fourthRequest), is(true));

    assertThat("First request should be at the same position",
      firstRequest.getPosition(), is(1));

    assertThat("Third request should have moved up the queue",
      thirdRequest.getPosition(), is(2));

    assertThat("Fourth request should have moved up the queue",
      fourthRequest.getPosition(), is(3));
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
