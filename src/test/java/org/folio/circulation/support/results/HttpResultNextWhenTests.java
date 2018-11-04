package org.folio.circulation.support.results;

import static api.support.matchers.FailureMatcher.isErrorFailureContaining;
import static org.folio.circulation.support.HttpResult.failed;
import static org.folio.circulation.support.HttpResult.succeeded;
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
        value -> { throw exampleException("Should not execute"); });

    assertThat(result.succeeded(), is(true));
    assertThat(result.value(), is(20));
  }

  @Test
  public void shouldApplyWhenFalseActionWhenConditionIsFalse() {
    final HttpResult<Integer> result = succeeded(10)
      .nextWhen(value -> succeeded(false),
        value -> { throw exampleException("Should not execute"); },
        value -> succeeded(value + 10));

    assertThat(result.succeeded(), is(true));
    assertThat(result.value(), is(20));
  }

  @Test
  public void shouldFailWhenAlreadyFailed() {
    final HttpResult<Integer> result = alreadyFailed()
      .nextWhen(value -> succeeded(true),
        value -> succeeded(value + 10),
        value -> succeeded(value + 10));

    assertThat(result, isErrorFailureContaining("Already failed"));
  }

  @Test
  public void shouldFailWhenConditionFailed() {
    final HttpResult<Integer> result = succeeded(10)
      .nextWhen(value -> failed(exampleFailure("Condition failed")),
        value -> succeeded(value + 10),
        value -> succeeded(value + 10));

    assertThat(result, isErrorFailureContaining("Condition failed"));
  }

  private WritableHttpResult<Integer> alreadyFailed() {
    return failed(exampleFailure("Already failed"));
  }

  private ServerErrorFailure exampleFailure(String message) {
    return new ServerErrorFailure(exampleException(message));
  }

  private RuntimeException exampleException(String message) {
    return new RuntimeException(message);
  }
}
