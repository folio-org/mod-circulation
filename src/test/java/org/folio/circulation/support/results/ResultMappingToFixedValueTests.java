package org.folio.circulation.support.results;

import static api.support.matchers.FailureMatcher.isErrorFailureContaining;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultExamples.alreadyFailed;
import static org.folio.circulation.support.results.ResultExamples.somethingWentWrong;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;

public class ResultMappingToFixedValueTests {
  @Test
  public void shouldSucceedWhenMapIsApplied() {
    final Result<Integer> mappedResult = succeeded(10)
      .toFixedValue(() -> 20);

    assertThat(mappedResult.succeeded(), is(true));
    assertThat(mappedResult.value(), is(20));
  }

  @Test
  public void shouldFailWhenAlreadyFailed() {
    final Result<Integer> result = alreadyFailed()
      .toFixedValue(() -> 20);

    assertThat(result, isErrorFailureContaining("Already failed"));
  }

  @Test
  public void shouldFailWhenExceptionThrownDuringMapping() {
    final Result<Integer> result = succeeded(10)
      .toFixedValue(() -> { throw somethingWentWrong(); });

    assertThat(result, isErrorFailureContaining("Something went wrong"));
  }
}
