package org.folio.circulation.support.results;

import static api.support.matchers.FailureMatcher.isErrorFailureContaining;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.results.ResultExamples.actionFailed;
import static org.folio.circulation.support.results.ResultExamples.alreadyFailed;
import static org.folio.circulation.support.results.ResultExamples.somethingWentWrong;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.folio.circulation.support.Result;
import org.junit.Test;

public class ResultFailureMappingTests {
  @Test
  public void shouldSucceedWhenAlreadySuccessful() {
    final Result<Integer> mappedResult = succeeded(10)
      .mapFailure(failure -> actionFailed());

    assertThat(mappedResult.succeeded(), is(true));
    assertThat(mappedResult.value(), is(10));
  }

  @Test
  public void shouldBeMappedWhenAlreadyFailed() {
    final Result<Integer> result = alreadyFailed()
      .mapFailure(failure -> actionFailed());

    assertThat(result, isErrorFailureContaining("Action failed"));
  }

  @Test
  public void shouldFailWhenExceptionThrownDuringMapping() {
    final Result<Integer> result = alreadyFailed()
      .mapFailure(failure -> { throw somethingWentWrong(); });

    assertThat(result, isErrorFailureContaining("Something went wrong"));
  }

  @Test
  public void shouldIgnoreExceptionDuringMappingWhenAlreadySuccessful() {
    final Result<Integer> result = succeeded(10)
      .mapFailure(failure -> { throw somethingWentWrong(); });

    assertThat(result.succeeded(), is(true));
    assertThat(result.value(), is(10));
  }
}
