package org.folio.circulation.support.results;

import static api.support.matchers.FailureMatcher.isErrorFailureContaining;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static org.folio.circulation.support.HttpResult.succeeded;
import static org.folio.circulation.support.results.ResultExamples.alreadyFailed;
import static org.folio.circulation.support.results.ResultExamples.shouldNotExecute;
import static org.folio.circulation.support.results.ResultExamples.somethingWentWrong;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.concurrent.ExecutionException;

import org.folio.circulation.support.HttpResult;
import org.junit.Test;

public class HttpResultAfterTests {
  @Test
  public void shouldSucceedWhenNextStepIsSuccessful()
    throws ExecutionException,
    InterruptedException {

    final HttpResult<Integer> result = succeeded(10)
      .after(value -> completedFuture(succeeded(value + 10)))
      .get();

    assertThat(result.succeeded(), is(true));
    assertThat(result.value(), is(20));
  }

  @Test
  public void shouldFailWhenAlreadyFailed()
    throws ExecutionException,
    InterruptedException {
    
    final HttpResult<Integer> result = alreadyFailed()
      .<Integer>after(value -> { throw shouldNotExecute(); })
      .get();

    assertThat(result, isErrorFailureContaining("Already failed"));
  }

  @Test
  public void shouldFailWhenExceptionThrownDuringNextStep()
    throws ExecutionException,
    InterruptedException {

    final HttpResult<Integer> result = succeeded(10)
      .<Integer>after(value -> { throw somethingWentWrong(); })
      .get();

    assertThat(result, isErrorFailureContaining("Something went wrong"));
  }

  @Test
  public void shouldFailWhenFutureFailsAsynchronously()
    throws ExecutionException,
    InterruptedException {

    final HttpResult<Integer> result = succeeded(10)
      .<Integer>after(value -> supplyAsync(() -> { throw somethingWentWrong(); }))
      .get();

    assertThat(result, isErrorFailureContaining("Something went wrong"));
  }
}
