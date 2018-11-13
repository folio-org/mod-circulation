package org.folio.circulation.support.results;

import static org.folio.circulation.support.HttpResult.succeeded;
import static org.folio.circulation.support.results.ResultExamples.alreadyFailed;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Test;

public class HttpResultGetValueTests {
  @Test
  public void shouldBeValueWhenSucceeded() {
    final Integer result = succeeded(10)
      .orElse(5);

    assertThat(result, is(10));
  }

  @Test
  public void shouldBeAlternativeWhenFailed() {
    final Integer result = alreadyFailed()
      .orElse(5);

    assertThat(result, is(5));
  }
}
