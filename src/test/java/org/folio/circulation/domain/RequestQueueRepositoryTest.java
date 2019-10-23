package org.folio.circulation.domain;

import static java.util.Arrays.asList;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Duration.FIVE_HUNDRED_MILLISECONDS;
import static org.awaitility.Duration.TWO_HUNDRED_MILLISECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.folio.circulation.support.Result;
import org.folio.circulation.support.ServerErrorFailure;
import org.junit.Test;
import org.mockito.ArgumentMatchers;

import api.support.builders.RequestBuilder;

public class RequestQueueRepositoryTest {
  /**
   * Tests the {@code updateRequestsWithChangedPositions} method. Ensures
   * that each call to update the position of the request in the repository
   * is done in order by introducing wait times on specific update calls.
   * While this is not an ideal way to test this code, it does show that the
   * old code will cause this test to fail, while the newer code passes this
   * test. If the logic changes around updating request queue positioning,
   * then this test may be obsolete. This test is strictly for regression
   * under the current request queue positioning design.
   *
   * @throws Exception on catastrophic unexpected error
   */
  @Test
  public void testUpdateRequestsWithChangedPositions() throws Exception {
    final RequestRepository requestRepository = mock(RequestRepository.class);

    final UUID itemId = UUID.randomUUID();

    final Request firstRequest = requestAtPosition(itemId, 1);
    final Request secondRequest = requestAtPosition(itemId, 2);
    final Request thirdRequest = requestAtPosition(itemId, 3);
    final Request fourthRequest = requestAtPosition(itemId, 4);

    final AtomicBoolean secondMoved = new AtomicBoolean(false);
    final AtomicBoolean thirdMoved = new AtomicBoolean(false);

    // Mock the update method so we return at different times. For requests
    // further down the queue, we check to see if the previous request has
    // already been processed. If it has not, return an error, otherwise
    // return the updated request. In this way, we can show whether or not
    // updateRequestsWithChangedPositions is executing updates in order or
    // all at once.
    when(requestRepository.update(ArgumentMatchers.any(Request.class)))
      .thenAnswer(invokation -> {
        final Object [] args = invokation.getArguments();

        if (((Request) args[0]).getId().equals(secondRequest.getId())) {
          // Return the new "first" request after a 500 ms delay
          return CompletableFuture.supplyAsync(() -> {
            await().pollDelay(FIVE_HUNDRED_MILLISECONDS).until(() -> true);
            secondMoved.compareAndSet(false, true);
            return Result.of(() -> secondRequest);
          });
        } else if (((Request) args[0]).getId().equals(thirdRequest.getId())) {
          // Return the new "second" request after a 200 ms delay
          return CompletableFuture.supplyAsync(() -> {
            if (secondMoved.get()) {
              await().pollDelay(TWO_HUNDRED_MILLISECONDS).until(() -> true);
              thirdMoved.compareAndSet(false, true);
              return Result.of(() -> thirdRequest);
            } else {
              return Result.failed(
                  new ServerErrorFailure("request already at position 2"));
            }
          });
        } else {
          // Return the new "third" request immediately
          return CompletableFuture.supplyAsync(() -> {
            if (secondMoved.get() && thirdMoved.get()) {
              return Result.of(() -> fourthRequest);
            } else {
              return Result.failed(
                  new ServerErrorFailure("request already at position 3"));
            }
          });
        }
      });

    final RequestQueue requestQueue = new RequestQueue(
        asList(firstRequest, secondRequest, thirdRequest, fourthRequest));

    final RequestQueueRepository requestQueueRepository =
        new RequestQueueRepository(requestRepository);

    // Cause potential request queue chaos
    requestQueue.remove(firstRequest);

    // Execute the reorder
    final CompletableFuture<Result<RequestQueue>> cf = requestQueueRepository
        .updateRequestsWithChangedPositions(requestQueue);

    final Result<RequestQueue> result = cf.get();

    assertNotNull(result);
    assertTrue(result.succeeded());

    final RequestQueue reorderedQueue = result.value();

    assertNotNull(reorderedQueue);
    assertEquals(3, reorderedQueue.getRequests().size());

    // On success, the positions should be in numerical order
    int position = 1;
    for (Request request : reorderedQueue.getRequests()) {
      assertEquals(Integer.valueOf(position++), request.getPosition());
    }
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
