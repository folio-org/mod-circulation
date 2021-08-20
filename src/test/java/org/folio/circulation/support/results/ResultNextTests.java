package org.folio.circulation.support.results;

import static api.support.matchers.FailureMatcher.isErrorFailureContaining;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultExamples.alreadyFailed;
import static org.folio.circulation.support.results.ResultExamples.somethingWentWrong;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.Test;

class ResultNextTests {
  @Test
  void shouldSucceedWhenNextStepIsSuccessful() {
    final Result<Integer> result = succeeded(10)
      .next(value -> succeeded(value + 10));

    assertThat(result.succeeded(), is(true));
    assertThat(result.value(), is(20));
  }

  @Test
  void shouldFailWhenAlreadyFailed() {
    final Result<Integer> result = alreadyFailed()
      .next(value -> succeeded(value + 10));

    assertThat(result, isErrorFailureContaining("Already failed"));
  }

  @Test
  void shouldFailWhenExceptionThrownDuringNextStep() {
    final Result<Integer> result = succeeded(10)
      .next(value -> { throw somethingWentWrong(); });

    assertThat(result, isErrorFailureContaining("Something went wrong"));
  }
}
