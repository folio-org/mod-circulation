package org.folio.circulation.support.results;

import static api.support.matchers.FailureMatcher.isErrorFailureContaining;
import static org.folio.circulation.support.HttpResult.succeeded;
import static org.folio.circulation.support.results.ResultExamples.alreadyFailed;
import static org.folio.circulation.support.results.ResultExamples.conditionFailed;
import static org.folio.circulation.support.results.ResultExamples.shouldNotExecute;
import static org.folio.circulation.support.results.ResultExamples.somethingWentWrong;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.folio.circulation.support.HttpResult;
import org.junit.Test;

public class HttpResultNextWhenTests {
  @Test
  public void shouldApplyWhenTrueActionWhenConditionIsTrue() {
    final HttpResult<Integer> result = succeeded(10)
      .nextWhen(value -> succeeded(true),
        value -> succeeded(value + 10),
        value -> shouldNotExecute());

    assertThat(result.succeeded(), is(true));
    assertThat(result.value(), is(20));
  }

  @Test
  public void shouldApplyWhenFalseActionWhenConditionIsFalse() {
    final HttpResult<Integer> result = succeeded(10)
      .nextWhen(value -> succeeded(false),
        value -> shouldNotExecute(),
        value -> succeeded(value + 10));

    assertThat(result.succeeded(), is(true));
    assertThat(result.value(), is(20));
  }

  @Test
  public void shouldFailWhenAlreadyFailed() {
    final HttpResult<Integer> result = alreadyFailed()
      .nextWhen(value -> succeeded(true),
        value -> shouldNotExecute(),
        value -> shouldNotExecute());

    assertThat(result, isErrorFailureContaining("Already failed"));
  }

  @Test
  public void shouldFailWhenConditionFailed() {
    final HttpResult<Integer> result = succeeded(10)
      .nextWhen(value -> conditionFailed(),
        value -> succeeded(value + 10),
        value -> succeeded(value + 10));

    assertThat(result, isErrorFailureContaining("Condition failed"));
  }

  @Test
  public void shouldFailWhenTrueActionFailed() {
    final HttpResult<Integer> result = succeeded(10)
      .nextWhen(value -> succeeded(true),
        value -> {throw somethingWentWrong(); },
        value -> shouldNotExecute());

    assertThat(result, isErrorFailureContaining("Something went wrong"));
  }

  @Test
  public void shouldFailWhenFalseActionFailed() {
    final HttpResult<Integer> result = succeeded(10)
      .nextWhen(value -> succeeded(false),
        value -> shouldNotExecute(),
        value -> {throw somethingWentWrong(); });

    assertThat(result, isErrorFailureContaining("Something went wrong"));
  }
}
