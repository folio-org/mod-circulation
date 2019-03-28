package org.folio.circulation.support.results;

import static api.support.matchers.FailureMatcher.isErrorFailureContaining;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.results.ResultExamples.alreadyFailed;
import static org.folio.circulation.support.results.ResultExamples.somethingWentWrong;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.folio.circulation.support.Result;
import org.junit.Test;

public class ResultNextTests {
  @Test
  public void shouldSucceedWhenNextStepIsSuccessful() {
    final Result<Integer> result = succeeded(10)
      .next(value -> succeeded(value + 10));

    assertThat(result.succeeded(), is(true));
    assertThat(result.value(), is(20));
  }

  @Test
  public void shouldFailWhenAlreadyFailed() {
    final Result<Integer> result = alreadyFailed()
      .next(value -> succeeded(value + 10));

    assertThat(result, isErrorFailureContaining("Already failed"));
  }

  @Test
  public void shouldFailWhenExceptionThrownDuringNextStep() {
    final Result<Integer> result = succeeded(10)
      .next(value -> { throw somethingWentWrong(); });

    assertThat(result, isErrorFailureContaining("Something went wrong"));
  }
}
