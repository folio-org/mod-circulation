package org.folio.circulation.support.logging;

import org.junit.Test;

import static org.folio.circulation.support.logging.LogMessageSanitizer.sanitizeLogParameter;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

public class LogMessageSanitizerTests {
  @Test
  public void shouldRemoveNewLineCharactersFromLogMessageParameters() {
    final String unsanitizedParameter = "Some \n multiple \r line string";

    assertThat(sanitizeLogParameter(unsanitizedParameter),
      is("Some _ multiple _ line string"));
  }

  @Test
  public void shouldRemoveTabCharactersFromLogMessageParameters() {
    final String unsanitizedParameter = "Some \t tabbed \t string";

    assertThat(sanitizeLogParameter(unsanitizedParameter),
      is("Some _ tabbed _ string"));
  }

  @Test
  public void shouldNotSanitizeNullParameters() {
    assertThat(sanitizeLogParameter(null), nullValue());
  }
}
