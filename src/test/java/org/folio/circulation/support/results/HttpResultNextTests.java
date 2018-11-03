package org.folio.circulation.support.results;

import static org.folio.circulation.support.HttpResult.failed;
import static org.folio.circulation.support.HttpResult.succeeded;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.WritableHttpResult;
import org.junit.Test;

public class HttpResultNextTests {
  @Test
  public void shouldSucceedWhenNextStepIsSuccessful() {
    final HttpResult<Integer> result = succeeded(10)
      .next(value -> succeeded(value + 10));

    assertThat(result.succeeded(), is(true));
    assertThat(result.value(), is(20));
  }

  @Test
  public void shouldFailWhenAlreadyFailed() {
    final HttpResult<Integer> result = failedResult()
      .next(value -> succeeded(value + 10));

    assertThat(result.failed(), is(true));
    assertThat(result.cause(), instanceOf(ServerErrorFailure.class));
  }

  @Test
  public void shouldFailWhenExceptionThrownDuringNextStep() {
    final HttpResult<Integer> result = succeeded(10)
      .next(value -> { throw exampleException(); });

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
