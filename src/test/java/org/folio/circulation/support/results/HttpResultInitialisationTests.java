package org.folio.circulation.support.results;

import static api.support.matchers.FailureMatcher.isErrorFailureContaining;
import static org.folio.circulation.support.results.ResultExamples.*;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.HttpResult;
import org.junit.Test;

public class HttpResultInitialisationTests {
  @Test
  public void shouldSucceedWhenInitialValue() {
    final HttpResult<Integer> result = HttpResult.of(() -> 10);

    assertThat(result.succeeded(), is(true));
    assertThat(result.value(), is(10));
  }

  @Test
  public void shouldFailWhenExceptionThrownForInitialValue() {
    final HttpResult<String> result = HttpResult.of(() -> {
      throw exampleException("Initialisation failed");
    });

    assertThat(result, isErrorFailureContaining("Initialisation failed"));
  }

  @Test
  public void shouldSucceedWhenInitialValueAsync()
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    final CompletableFuture<HttpResult<Integer>> futureResult = HttpResult.ofAsync(() -> 10);

    final HttpResult<Integer> result = futureResult.get(1, TimeUnit.SECONDS);

    assertThat(result.succeeded(), is(true));
    assertThat(result.value(), is(10));
  }
}
