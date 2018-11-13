package org.folio.circulation.support.results;

import static api.support.matchers.FailureMatcher.isErrorFailureContaining;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static org.folio.circulation.support.HttpResult.succeeded;
import static org.folio.circulation.support.results.ResultExamples.alreadyFailed;
import static org.folio.circulation.support.results.ResultExamples.conditionFailed;
import static org.folio.circulation.support.results.ResultExamples.shouldNotExecute;
import static org.folio.circulation.support.results.ResultExamples.somethingWentWrong;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.concurrent.ExecutionException;

import org.folio.circulation.support.HttpResult;
import org.junit.Test;

public class HttpResultAfterWhenTests {
  @Test
  public void shouldApplyWhenTrueActionWhenConditionIsTrue()
    throws ExecutionException,
    InterruptedException {

    final HttpResult<Integer> result = succeeded(10)
      .afterWhen(value -> completedFuture(succeeded(true)),
        value -> completedFuture(succeeded(value + 10)),
        value -> { throw shouldNotExecute(); })
      .get();

    assertThat(result.succeeded(), is(true));
    assertThat(result.value(), is(20));
  }

  @Test
  public void shouldApplyWhenFalseActionWhenConditionIsFalse()
    throws ExecutionException,
    InterruptedException {

    final HttpResult<Integer> result = succeeded(10)
      .afterWhen(value -> completedFuture(succeeded(false)),
        value -> { throw shouldNotExecute(); },
        value -> completedFuture(succeeded(value + 10)))
      .get();

    assertThat(result.succeeded(), is(true));
    assertThat(result.value(), is(20));
  }

  @Test
  public void shouldFailWhenAlreadyFailed()
    throws ExecutionException,
    InterruptedException {

    final HttpResult<Integer> result = alreadyFailed()
      .afterWhen(value -> completedFuture(succeeded(true)),
        value -> { throw shouldNotExecute(); },
        value -> { throw shouldNotExecute(); })
      .get();

    assertThat(result, isErrorFailureContaining("Already failed"));
  }

  @Test
  public void shouldFailWhenConditionFailed()
    throws ExecutionException,
    InterruptedException {

    final HttpResult<Integer> result = succeeded(10)
      .afterWhen(value -> completedFuture(conditionFailed()),
        value -> { throw shouldNotExecute(); },
        value -> { throw shouldNotExecute(); })
      .get();

    assertThat(result, isErrorFailureContaining("Condition failed"));
  }

  @Test
  public void shouldFailWhenTrueFutureFailsAsynchronously()
    throws ExecutionException,
    InterruptedException {

    final HttpResult<Integer> result = succeeded(10)
      .afterWhen(value -> completedFuture(succeeded(true)),
        value -> supplyAsync(() -> { throw somethingWentWrong(); }),
        value -> { throw shouldNotExecute(); })
      .get();

    assertThat(result, isErrorFailureContaining("Something went wrong"));
  }

  @Test
  public void shouldFailWhenFalseFutureFailsAsynchronously()
    throws ExecutionException,
    InterruptedException {

    final HttpResult<Integer> result = succeeded(10)
      .afterWhen(value -> completedFuture(succeeded(false)),
        value -> { throw shouldNotExecute(); },
        value -> supplyAsync(() -> { throw somethingWentWrong(); }))
      .get();

    assertThat(result, isErrorFailureContaining("Something went wrong"));
  }

  @Test
  public void shouldFailWhenConditionFutureFailsAsynchronously()
    throws ExecutionException,
    InterruptedException {

    final HttpResult<Integer> result = succeeded(10)
      .afterWhen(value -> supplyAsync(() -> { throw somethingWentWrong(); }),
        value -> { throw shouldNotExecute(); },
        value -> { throw shouldNotExecute(); })
      .get();

    assertThat(result, isErrorFailureContaining("Something went wrong"));
  }

  @Test
  public void shouldFailWhenTrueActionFailed()
    throws ExecutionException,
    InterruptedException {

    final HttpResult<Integer> result = succeeded(10)
      .afterWhen(value -> completedFuture(succeeded(true)),
        value -> { throw somethingWentWrong(); },
        value -> {throw shouldNotExecute(); })
      .get();

    assertThat(result, isErrorFailureContaining("Something went wrong"));
  }

  @Test
  public void shouldFailWhenFalseActionFailed()
    throws ExecutionException,
    InterruptedException {

    final HttpResult<Integer> result = succeeded(10)
      .afterWhen(value -> completedFuture(succeeded(false)),
        value -> { throw shouldNotExecute(); },
        value -> {throw somethingWentWrong(); })
      .get();

    assertThat(result, isErrorFailureContaining("Something went wrong"));
  }
}
