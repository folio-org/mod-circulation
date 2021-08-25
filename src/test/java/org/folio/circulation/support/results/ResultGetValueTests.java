package org.folio.circulation.support.results;

import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultExamples.alreadyFailed;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.Test;

class ResultGetValueTests {
  @Test
  void shouldBeValueWhenSucceeded() {
    final Integer result = succeeded(10)
      .orElse(5);

    assertThat(result, is(10));
  }

  @Test
  void shouldBeAlternativeWhenFailed() {
    final Integer result = alreadyFailed()
      .orElse(5);

    assertThat(result, is(5));
  }
}
