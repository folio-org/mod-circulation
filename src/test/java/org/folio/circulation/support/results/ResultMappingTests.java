package org.folio.circulation.support.results;

import static api.support.matchers.FailureMatcher.isErrorFailureContaining;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultExamples.alreadyFailed;
import static org.folio.circulation.support.results.ResultExamples.somethingWentWrong;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.Test;

public class ResultMappingTests {
  @Test
  void shouldSucceedWhenMapIsApplied() {
    final Result<Integer> mappedResult = succeeded(10)
      .map(value -> value + 10);

    assertThat(mappedResult.succeeded(), is(true));
    assertThat(mappedResult.value(), is(20));
  }

  @Test
  void shouldFailWhenAlreadyFailed() {
    final Result<Integer> result = alreadyFailed()
      .map(value -> value + 10);

    assertThat(result, isErrorFailureContaining("Already failed"));
  }

  @Test
  void shouldFailWhenExceptionThrownDuringMapping() {
    final Result<Integer> result = succeeded(10)
      .map(value -> { throw somethingWentWrong(); });

    assertThat(result, isErrorFailureContaining("Something went wrong"));
  }
}
