package org.folio.circulation.support.results;

import static api.support.matchers.FailureMatcher.isErrorFailureContaining;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

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
      throw new RuntimeException("Initialisation failed");
    });

    assertThat(result, isErrorFailureContaining("Initialisation failed"));
  }
}
