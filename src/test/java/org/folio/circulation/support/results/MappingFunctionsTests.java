package org.folio.circulation.support.results;

import static org.folio.circulation.support.results.MappingFunctions.toFixedValue;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.Test;


class MappingFunctionsTests {
  @Test
  void canMapToAFixedValue() {
    assertThat(toFixedValue(() -> 10).apply(30), is(10));
  }
}
