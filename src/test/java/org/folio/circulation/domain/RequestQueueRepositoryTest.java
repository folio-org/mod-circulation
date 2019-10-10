package org.folio.circulation.domain;

import static java.util.Arrays.asList;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Duration.FIVE_HUNDRED_MILLISECONDS;
import static org.awaitility.Duration.TWO_HUNDRED_MILLISECONDS;
import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.of;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.folio.circulation.support.Result;
import org.folio.circulation.support.ServerErrorFailure;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.stubbing.Answer;

import api.support.builders.RequestBuilder;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
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
            return of(() -> secondRequest);
          });
        } else if (((Request) args[0]).getId().equals(thirdRequest.getId())) {
          // Return the new "second" request after a 200 ms delay
          return CompletableFuture.supplyAsync(() -> {
            if (secondMoved.get()) {
              await().pollDelay(TWO_HUNDRED_MILLISECONDS).until(() -> true);
              thirdMoved.compareAndSet(false, true);
              return of(() -> thirdRequest);
            } else {
              return failed(
                  new ServerErrorFailure("request already at position 2"));
            }
          });
        } else {
          // Return the new "third" request immediately
          return CompletableFuture.supplyAsync(() -> {
            if (secondMoved.get() && thirdMoved.get()) {
              return of(() -> fourthRequest);
            } else {
              return failed(
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

  @Test
  @Parameters({
    "1", "2", "3", "4"
  })
  public void testReorderRequestsShouldFailWhenSomeUpdatesFailed(int positionToFail) throws Exception {
    final UUID itemId = UUID.randomUUID();

    final Request firstRequest = requestAtPosition(itemId, 2);
    final Request secondRequest = requestAtPosition(itemId, 3);
    final Request thirdRequest = requestAtPosition(itemId, 4);
    final Request fourthRequest = requestAtPosition(itemId, 5);

    // Change request positions, so the logic will pick requests,
    firstRequest.changePosition(1);
    secondRequest.changePosition(2);
    thirdRequest.changePosition(3);
    fourthRequest.changePosition(4);

    final RequestQueue requestQueue = new RequestQueue(asList(firstRequest, secondRequest, thirdRequest, fourthRequest));

    RequestRepository requestRepository = mock(RequestRepository.class);
    RequestQueueRepository requestQueueRepository = new RequestQueueRepository(requestRepository);

    // Mock repository, so it will fail on request with the specified position
    when(requestRepository.update(any(Request.class)))
      .thenAnswer(failOnPosition(positionToFail));

    // Execute the logic
    CompletableFuture<Result<RequestQueue>> completableFutureResult = requestQueueRepository
      .reorderRequests(requestQueue);

    // Assert that result is failed with ServerErrorFailure
    Result<RequestQueue> result = completableFutureResult.get(5, TimeUnit.SECONDS);
    assertTrue(result.failed());
    assertTrue(result.cause() instanceof ServerErrorFailure);
    assertEquals(((ServerErrorFailure) result.cause()).getReason(),
      "Some requests have not been updated. Fetch the queue and try again.");
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

  private Answer<CompletableFuture<Result<Request>>> failOnPosition(int positionToFail) {
    return mock -> {
      Request request = mock.getArgument(0);
      // Emulate DB error on second request processing.
      if (request.hasPosition() && request.getPosition() == positionToFail) {
        return completedFuture(failed(new ServerErrorFailure("DB failure")));
      }

      return completedFuture(of(() -> request));
    };
  }
}
