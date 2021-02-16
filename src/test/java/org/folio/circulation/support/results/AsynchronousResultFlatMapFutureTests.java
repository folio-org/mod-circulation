package org.folio.circulation.support.results;

import static api.support.matchers.FailureMatcher.isFailureContaining;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.circulation.support.results.AsynchronousResult.successful;
import static org.folio.circulation.support.results.AsynchronousResultExamples.alreadyFailed;
import static org.folio.circulation.support.results.AsynchronousResultTestHelper.getCause;
import static org.folio.circulation.support.results.AsynchronousResultTestHelper.getValue;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultExamples.shouldNotExecute;
import static org.folio.circulation.support.results.ResultExamples.somethingWentWrong;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.Test;

class AsynchronousResultFlatMapFutureTests {
  @Test
  void shouldSucceedWhenNextStepIsSuccessful() {
    final var result = successful(10)
      .flatMapFuture(value -> completedFuture(succeeded(value + 10)));

    assertThat(getValue(result, 1, SECONDS), is(20));
  }

  @Test
  void shouldFailWhenAlreadyFailed() {
    final var result = alreadyFailed()
      .<Integer>flatMapFuture(value -> { throw shouldNotExecute(); });

    assertThat(getCause(result, 1, SECONDS), isFailureContaining("Already failed"));
  }

  @Test
  void shouldFailWhenExceptionThrownDuringNextStep() {
    final var result = successful(10)
      .<Integer>flatMapFuture(value -> { throw somethingWentWrong(); });

    assertThat(getCause(result, 1, SECONDS), isFailureContaining("Something went wrong"));
  }

  @Test
  void shouldFailWhenFutureFailsAsynchronously() {
    final var result = successful(10)
      .<Integer>flatMapFuture(value -> supplyAsync(() -> { throw somethingWentWrong(); }));

    assertThat(getCause(result, 1, SECONDS), isFailureContaining("Something went wrong"));
  }
}
