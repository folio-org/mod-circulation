package org.folio.circulation.support.results;

import static api.support.matchers.FailureMatcher.isErrorFailureContaining;
import static org.folio.circulation.support.HttpResult.succeeded;
import static org.folio.circulation.support.results.ResultExamples.alreadyFailed;
import static org.folio.circulation.support.results.ResultExamples.conditionFailed;
import static org.folio.circulation.support.results.ResultExamples.exampleFailure;
import static org.folio.circulation.support.results.ResultExamples.somethingWentWrong;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.folio.circulation.support.HttpResult;
import org.junit.Test;

public class HttpResultFailWhenTests {
  @Test
  public void shouldPassThroughResultWhenConditionIsFalse() {
    final HttpResult<Integer> result = succeeded(10)
      .failWhen(value -> succeeded(false),
        value -> exampleFailure("Specified failure"));

    assertThat(result.succeeded(), is(true));
    assertThat(result.value(), is(10));
  }

  @Test
  public void shouldApplyFailureWhenConditionIsTrue() {
    final HttpResult<Integer> result = succeeded(10)
      .failWhen(value -> succeeded(true),
        value -> exampleFailure("Specified failure"));

    assertThat(result, isErrorFailureContaining("Specified failure"));
  }

  @Test
  public void shouldFailWhenAlreadyFailed() {
    final HttpResult<Integer> result = alreadyFailed()
      .failWhen(value -> succeeded(false),
        value -> exampleFailure("Specified failure"));

    assertThat(result, isErrorFailureContaining("Already failed"));
  }

  @Test
  public void shouldFailWhenConditionFailed() {
    final HttpResult<Integer> result = succeeded(10)
      .failWhen(value -> conditionFailed(),
        value -> exampleFailure("Specified failure"));

    assertThat(result, isErrorFailureContaining("Condition failed"));
  }

  @Test
  public void shouldFailWhenCreatingFailureFails() {
    final HttpResult<Integer> result = succeeded(10)
      .failWhen(value -> succeeded(true),
        value -> { throw somethingWentWrong(); });

    assertThat(result, isErrorFailureContaining("Something went wrong"));
  }
}
