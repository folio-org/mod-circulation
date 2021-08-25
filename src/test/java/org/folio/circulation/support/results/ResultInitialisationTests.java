package org.folio.circulation.support.results;

import static api.support.matchers.FailureMatcher.isErrorFailureContaining;
import static org.folio.circulation.support.results.Result.of;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.ResultExamples.*;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

class ResultInitialisationTests {
  @Test
  void shouldSucceedWhenInitialValue() {
    final Result<Integer> result = of(() -> 10);

    assertThat(result.succeeded(), is(true));
    assertThat(result.value(), is(10));
  }

  @Test
  void shouldFailWhenExceptionThrownForInitialValue() {
    final Result<String> result = of(() -> {
      throw exampleException("Initialisation failed");
    });

    assertThat(result, isErrorFailureContaining("Initialisation failed"));
  }

  @Test
  void shouldSucceedWhenInitialValueAsync()
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    final CompletableFuture<Result<Integer>> futureResult = ofAsync(() -> 10);

    final Result<Integer> result = futureResult.get(1, TimeUnit.SECONDS);

    assertThat(result.succeeded(), is(true));
    assertThat(result.value(), is(10));
  }
}
