package org.folio.circulation.support.results;

import static api.support.matchers.FailureMatcher.isErrorFailureContaining;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.circulation.support.results.Result.of;
import static org.folio.circulation.support.results.AsynchronousResultBindings.safelyInitialise;
import static org.folio.circulation.support.results.ResultExamples.actionFailed;
import static org.folio.circulation.support.results.ResultExamples.somethingWentWrong;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.junit.Test;

import lombok.val;

public class SafelyInitialiseAsynchronousResultTests {
  @Test
  public void shouldSucceedWhenSupplierSucceeds() throws ExecutionException,
    InterruptedException, TimeoutException {

    val result = safelyInitialise(() -> completedFuture(of(() -> 10)))
      .get(1, SECONDS);

    assertThat(result.succeeded(), is(true));
    assertThat(result.value(), is(10));
  }

  @Test
  public void shouldFailWhenSupplierFails() throws ExecutionException,
    InterruptedException, TimeoutException {

    val result = safelyInitialise(() -> completedFuture(actionFailed()))
      .get(1, SECONDS);

    assertThat(result, isErrorFailureContaining("Action failed"));
  }

  @Test
  public void shouldFailWhenSupplierIsNull() throws ExecutionException,
    InterruptedException, TimeoutException {

    val result = safelyInitialise(null)
      .get(1, SECONDS);

    assertThat(result, isErrorFailureContaining("The asynchronous result supplier should not be null"));
  }

  @Test
  public void shouldFailWhenSupplierThrowsException() throws ExecutionException,
    InterruptedException, TimeoutException {

    val result = safelyInitialise(() -> { throw somethingWentWrong(); })
      .get(1, SECONDS);

    assertThat(result, isErrorFailureContaining("Something went wrong"));
  }
}
