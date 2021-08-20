package org.folio.circulation.support.logging;

import org.junit.jupiter.api.Test;

import static org.folio.circulation.support.logging.LogMessageSanitizer.sanitizeLogParameter;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class LogMessageSanitizerTests {
  @Test
  void shouldRemoveNewLineCharactersFromLogMessageParameters() {
    final String unsanitizedParameter = "Some \n multiple \r line string";

    assertThat(sanitizeLogParameter(unsanitizedParameter),
      is("Some _ multiple _ line string"));
  }

  @Test
  void shouldRemoveTabCharactersFromLogMessageParameters() {
    final String unsanitizedParameter = "Some \t tabbed \t string";

    assertThat(sanitizeLogParameter(unsanitizedParameter),
      is("Some _ tabbed _ string"));
  }

  @Test
  void shouldNotSanitizeNullParameters() {
    assertThat(sanitizeLogParameter(null), nullValue());
  }
}
