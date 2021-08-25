package org.folio.circulation.support.results;

import static api.support.matchers.FailureMatcher.isErrorFailureContaining;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultExamples.alreadyFailed;
import static org.folio.circulation.support.results.ResultExamples.conditionFailed;
import static org.folio.circulation.support.results.ResultExamples.exampleFailure;
import static org.folio.circulation.support.results.ResultExamples.somethingWentWrong;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.Test;

class ResultFailWhenTests {
  @Test
  void shouldPassThroughResultWhenConditionIsFalse() {
    final Result<Integer> result = succeeded(10)
      .failWhen(value -> succeeded(false),
        value -> exampleFailure("Specified failure"));

    assertThat(result.succeeded(), is(true));
    assertThat(result.value(), is(10));
  }

  @Test
  void shouldApplyFailureWhenConditionIsTrue() {
    final Result<Integer> result = succeeded(10)
      .failWhen(value -> succeeded(true),
        value -> exampleFailure("Specified failure"));

    assertThat(result, isErrorFailureContaining("Specified failure"));
  }

  @Test
  void shouldFailWhenAlreadyFailed() {
    final Result<Integer> result = alreadyFailed()
      .failWhen(value -> succeeded(false),
        value -> exampleFailure("Specified failure"));

    assertThat(result, isErrorFailureContaining("Already failed"));
  }

  @Test
  void shouldFailWhenConditionFailed() {
    final Result<Integer> result = succeeded(10)
      .failWhen(value -> conditionFailed(),
        value -> exampleFailure("Specified failure"));

    assertThat(result, isErrorFailureContaining("Condition failed"));
  }

  @Test
  void shouldFailWhenCreatingFailureFails() {
    final Result<Integer> result = succeeded(10)
      .failWhen(value -> succeeded(true),
        value -> { throw somethingWentWrong(); });

    assertThat(result, isErrorFailureContaining("Something went wrong"));
  }
}
