package org.folio.circulation.support.results;

import static api.support.matchers.FailureMatcher.isErrorFailureContaining;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.HttpResult.succeeded;
import static org.folio.circulation.support.results.ResultExamples.alreadyFailed;
import static org.folio.circulation.support.results.ResultExamples.conditionFailed;
import static org.folio.circulation.support.results.ResultExamples.exampleFailure;
import static org.folio.circulation.support.results.ResultExamples.somethingWentWrong;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.concurrent.ExecutionException;

import org.folio.circulation.support.HttpResult;
import org.junit.Test;

public class HttpResultFailAfterTests {
  @Test
  public void shouldPassThroughResultWhenConditionIsFalse()
    throws ExecutionException,
    InterruptedException {

    final HttpResult<Integer> result = succeeded(10)
      .failAfter(value -> completedFuture(succeeded(false)),
        value -> exampleFailure("Specified failure"))
      .get();

    assertThat(result.succeeded(), is(true));
    assertThat(result.value(), is(10));
  }

  @Test
  public void shouldApplyFailureWhenConditionIsTrue()
    throws ExecutionException,
    InterruptedException {

    final HttpResult<Integer> result = succeeded(10)
      .failAfter(value -> completedFuture(succeeded(true)),
        value -> exampleFailure("Specified failure"))
      .get();

    assertThat(result, isErrorFailureContaining("Specified failure"));
  }

  @Test
  public void shouldFailWhenAlreadyFailed()
    throws ExecutionException,
    InterruptedException {

    final HttpResult<Integer> result = alreadyFailed()
      .failAfter(value -> completedFuture(succeeded(false)),
        value -> exampleFailure("Specified failure"))
      .get();

    assertThat(result, isErrorFailureContaining("Already failed"));
  }

  @Test
  public void shouldFailWhenConditionFailed()
    throws ExecutionException,
    InterruptedException {

    final HttpResult<Integer> result = succeeded(10)
      .failAfter(value -> completedFuture(conditionFailed()),
        value -> exampleFailure("Specified failure"))
      .get();

    assertThat(result, isErrorFailureContaining("Condition failed"));
  }

  @Test
  public void shouldFailWhenCreatingFailureFails()
    throws ExecutionException,
    InterruptedException {

    final HttpResult<Integer> result = succeeded(10)
      .failAfter(value -> completedFuture(succeeded(true)),
        value -> { throw somethingWentWrong(); })
      .get();

    assertThat(result, isErrorFailureContaining("Something went wrong"));
  }
}
