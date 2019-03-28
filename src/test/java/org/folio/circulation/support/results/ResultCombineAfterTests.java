package org.folio.circulation.support.results;

import static api.support.matchers.FailureMatcher.isErrorFailureContaining;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.results.ResultExamples.actionFailed;
import static org.folio.circulation.support.results.ResultExamples.alreadyFailed;
import static org.folio.circulation.support.results.ResultExamples.shouldNotExecute;
import static org.folio.circulation.support.results.ResultExamples.somethingWentWrong;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.concurrent.ExecutionException;

import org.folio.circulation.support.Result;
import org.junit.Test;

public class ResultCombineAfterTests {
  @Test
  public void shouldSucceedWhenNextStepIsSuccessful()
    throws ExecutionException,
    InterruptedException {

    final Result<Integer> result = succeeded(10)
      .combineAfter(value -> completedFuture(succeeded(20)),
        (v1, v2) -> v1 + v2)
      .get();

    assertThat(result.succeeded(), is(true));
    assertThat(result.value(), is(30));
  }

  @Test
  public void shouldFailWhenAlreadyFailed()
    throws ExecutionException,
    InterruptedException {

    final Result<Integer> result = alreadyFailed()
      .combineAfter(value -> completedFuture(succeeded(20)),
        (v1, v2) -> v1 + v2)
      .get();

    assertThat(result, isErrorFailureContaining("Already failed"));
  }

  @Test
  public void shouldFailWhenNextStepFails()
    throws ExecutionException,
    InterruptedException {

    final Result<Integer> result = succeeded(10)
      .<Integer, Integer>combineAfter(value -> completedFuture(actionFailed()),
        (v1, v2) -> { throw shouldNotExecute(); })
      .get();

    assertThat(result, isErrorFailureContaining("Action failed"));
  }


  @Test
  public void shouldFailWhenExceptionThrownDuringNextStep()
    throws ExecutionException,
    InterruptedException {

    final Result<Integer> result = succeeded(10)
      .<Integer, Integer>combineAfter(value -> { throw somethingWentWrong(); },
        (v1, v2) -> { throw shouldNotExecute(); })
      .get();

    assertThat(result, isErrorFailureContaining("Something went wrong"));
  }

  @Test
  public void shouldFailWhenExceptionThrownDuringCombination()
    throws ExecutionException,
    InterruptedException {

    final Result<Integer> result = succeeded(10)
      .<Integer, Integer>combineAfter(value -> completedFuture(succeeded(20)),
        (v1, v2) -> { throw somethingWentWrong(); })
      .get();

    assertThat(result, isErrorFailureContaining("Something went wrong"));
  }
}
