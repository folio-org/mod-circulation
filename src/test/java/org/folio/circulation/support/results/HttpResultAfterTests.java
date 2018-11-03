package org.folio.circulation.support.results;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.HttpResult.failed;
import static org.folio.circulation.support.HttpResult.succeeded;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.concurrent.ExecutionException;

import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.WritableHttpResult;
import org.junit.Test;

public class HttpResultAfterTests {
  @Test
  public void shouldSucceedWhenNextStepIsSuccessful()
    throws ExecutionException,
    InterruptedException {

    final HttpResult<Integer> result = succeeded(10)
      .after(value -> completedFuture(succeeded(value + 10)))
      .get();

    assertThat(result.succeeded(), is(true));
    assertThat(result.value(), is(20));
  }

  @Test
  public void shouldFailWhenAlreadyFailed()
    throws ExecutionException,
    InterruptedException {
    
    final HttpResult<Integer> result = failedResult()
      .after(value -> completedFuture(succeeded(value + 10)))
      .get();

    assertThat(result.failed(), is(true));
    assertThat(result.cause(), instanceOf(ServerErrorFailure.class));
  }

  @Test
  public void shouldFailWhenExceptionThrownDuringNextStep()
    throws ExecutionException,
    InterruptedException {

    final HttpResult<Integer> result = succeeded(10)
      .<Integer>after(value -> { throw exampleException(); })
      .get();

    assertThat(result.failed(), is(true));
    assertThat(result.cause(), instanceOf(ServerErrorFailure.class));
  }

  private WritableHttpResult<Integer> failedResult() {
    return failed(new ServerErrorFailure(exampleException()));
  }

  private RuntimeException exampleException() {
    return new RuntimeException("Something went wrong");
  }
}
