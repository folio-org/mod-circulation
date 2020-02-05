package org.folio.circulation.support;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.nio.charset.StandardCharsets;

import org.folio.rest.testing.UtilityClassTester;
import org.junit.Test;

public class StringUtilTests {
  @Test
  public void isUtilityClass() {
    UtilityClassTester.assertUtilityClass(StringUtil.class);
  }

  @Test
  public void urlEncode() {
    assertThat(StringUtil.urlEncode("abc", "q"), is(nullValue()));
    assertThat(StringUtil.urlEncode("key=a-umlaut-ä", StandardCharsets.ISO_8859_1.name()),
        is("key%3Da-umlaut-%E4"));
    assertThat(StringUtil.urlEncode("key=a-umlaut-ä"), is("key%3Da-umlaut-%C3%A4"));
  }
}
