package org.folio.circulation.support.results;

import static api.support.matchers.FailureMatcher.isErrorFailureContaining;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.results.ResultExamples.alreadyFailed;
import static org.folio.circulation.support.results.ResultExamples.conditionFailed;
import static org.folio.circulation.support.results.ResultExamples.throwOnExecution;
import static org.folio.circulation.support.results.ResultExamples.somethingWentWrong;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.folio.circulation.support.Result;
import org.junit.Test;

public class ResultNextWhenTests {
  @Test
  public void shouldApplyWhenTrueActionWhenConditionIsTrue() {
    final Result<Integer> result = succeeded(10)
      .nextWhen(value -> succeeded(true),
        value -> succeeded(value + 10),
        value -> throwOnExecution());

    assertThat(result.succeeded(), is(true));
    assertThat(result.value(), is(20));
  }

  @Test
  public void shouldApplyWhenFalseActionWhenConditionIsFalse() {
    final Result<Integer> result = succeeded(10)
      .nextWhen(value -> succeeded(false),
        value -> throwOnExecution(),
        value -> succeeded(value + 10));

    assertThat(result.succeeded(), is(true));
    assertThat(result.value(), is(20));
  }

  @Test
  public void shouldFailWhenAlreadyFailed() {
    final Result<Integer> result = alreadyFailed()
      .nextWhen(value -> succeeded(true),
        value -> throwOnExecution(),
        value -> throwOnExecution());

    assertThat(result, isErrorFailureContaining("Already failed"));
  }

  @Test
  public void shouldFailWhenConditionFailed() {
    final Result<Integer> result = succeeded(10)
      .nextWhen(value -> conditionFailed(),
        value -> succeeded(value + 10),
        value -> succeeded(value + 10));

    assertThat(result, isErrorFailureContaining("Condition failed"));
  }

  @Test
  public void shouldFailWhenTrueActionFailed() {
    final Result<Integer> result = succeeded(10)
      .nextWhen(value -> succeeded(true),
        value -> {throw somethingWentWrong(); },
        value -> throwOnExecution());

    assertThat(result, isErrorFailureContaining("Something went wrong"));
  }

  @Test
  public void shouldFailWhenFalseActionFailed() {
    final Result<Integer> result = succeeded(10)
      .nextWhen(value -> succeeded(false),
        value -> throwOnExecution(),
        value -> {throw somethingWentWrong(); });

    assertThat(result, isErrorFailureContaining("Something went wrong"));
  }
}
