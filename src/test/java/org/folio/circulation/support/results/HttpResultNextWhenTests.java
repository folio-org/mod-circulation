package org.folio.circulation.support.results;

import static org.folio.circulation.support.HttpResult.failed;
import static org.folio.circulation.support.HttpResult.succeeded;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.WritableHttpResult;
import org.junit.Test;

public class HttpResultNextWhenTests {
  @Test
  public void shouldApplyWhenTrueActionWhenConditionIsTrue() {
    final HttpResult<Integer> result = succeeded(10)
      .nextWhen(value -> succeeded(true),
        value -> succeeded(value + 10),
        value -> { throw exampleException(); });

    assertThat(result.succeeded(), is(true));
    assertThat(result.value(), is(20));
  }

  @Test
  public void shouldApplyWhenFalseActionWhenConditionIsFalse() {
    final HttpResult<Integer> result = succeeded(10)
      .nextWhen(value -> succeeded(false),
        value -> { throw exampleException(); },
        value -> succeeded(value + 10));

    assertThat(result.succeeded(), is(true));
    assertThat(result.value(), is(20));
  }

  @Test
  public void shouldFailWhenAlreadyFailed() {
    final HttpResult<Integer> result = failedResult()
      .nextWhen(value -> succeeded(true),
        value -> succeeded(value + 10),
        value -> succeeded(value + 10));

    assertThat(result.failed(), is(true));
    assertThat(result.cause(), instanceOf(ServerErrorFailure.class));

    final ServerErrorFailure cause = (ServerErrorFailure) result.cause();
    assertThat(cause.reason, containsString("Something went wrong"));
  }

  @Test
  public void shouldFailWhenConditionFailed() {
    final HttpResult<Integer> result = succeeded(10)
      .nextWhen(value -> failed(exampleFailure()),
        value -> succeeded(value + 10),
        value -> succeeded(value + 10));

    assertThat(result.failed(), is(true));
    assertThat(result.cause(), instanceOf(ServerErrorFailure.class));

    final ServerErrorFailure cause = (ServerErrorFailure) result.cause();
    assertThat(cause.reason, containsString("Something went wrong"));
  }

  private WritableHttpResult<Integer> failedResult() {
    return failed(exampleFailure());
  }

  private ServerErrorFailure exampleFailure() {
    return new ServerErrorFailure(exampleException());
  }

  private RuntimeException exampleException() {
    return new RuntimeException("Something went wrong");
  }
}
