package org.folio.circulation.support.results;

import static api.support.matchers.FailureMatcher.isErrorFailureContaining;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultExamples.alreadyFailed;
import static org.folio.circulation.support.results.ResultExamples.shouldNotExecute;
import static org.folio.circulation.support.results.ResultExamples.somethingWentWrong;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.Test;

class ResultAfterTests {
  @Test
  void shouldSucceedWhenNextStepIsSuccessful()
    throws ExecutionException,
    InterruptedException {

    final Result<Integer> result = succeeded(10)
      .after(value -> completedFuture(succeeded(value + 10)))
      .get();

    assertThat(result.succeeded(), is(true));
    assertThat(result.value(), is(20));
  }

  @Test
  void shouldFailWhenAlreadyFailed()
    throws ExecutionException,
    InterruptedException {

    final Result<Integer> result = alreadyFailed()
      .<Integer>after(value -> { throw shouldNotExecute(); })
      .get();

    assertThat(result, isErrorFailureContaining("Already failed"));
  }

  @Test
  void shouldFailWhenExceptionThrownDuringNextStep()
    throws ExecutionException,
    InterruptedException {

    final Result<Integer> result = succeeded(10)
      .<Integer>after(value -> { throw somethingWentWrong(); })
      .get();

    assertThat(result, isErrorFailureContaining("Something went wrong"));
  }

  @Test
  void shouldFailWhenFutureFailsAsynchronously()
    throws ExecutionException,
    InterruptedException {

    final Result<Integer> result = succeeded(10)
      .<Integer>after(value -> supplyAsync(() -> { throw somethingWentWrong(); }))
      .get();

    assertThat(result, isErrorFailureContaining("Something went wrong"));
  }
}
