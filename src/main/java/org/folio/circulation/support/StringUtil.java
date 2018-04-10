package org.folio.circulation.support;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class StringUtil {
  private StringUtil() {
    throw new UnsupportedOperationException();
  }

  /**
   * Encode source using www-form-urlencoded scheme and charset.
   *
   * @param source  String to encode
   * @param charset  name of the charset to use
   * @return the encoded String, or null if charset is not supported
   * @throws NullPointerException if source or charset is null
   */
  public static String urlencode(String source, String charset) {
    try {
      return URLEncoder.encode(source, charset);
    } catch (UnsupportedEncodingException e) {
      return null;
    }
  }

  /**
   * Encode source using www-form-urlencoded scheme and UTF-8 charset.
   *
   * @param source  String to encode
   * @return the encoded String
   * @throws NullPointerException if source is null
   */
  public static String urlencode(String source) {
    // This charset is always supported and will never trigger an
    // UnsupportedEncodingException.
    return urlencode(source, StandardCharsets.UTF_8.name());
  }
}
