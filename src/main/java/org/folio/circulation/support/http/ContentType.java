package org.folio.circulation.support.http;

/**
 * Strings for HTTP content-type header.
 */
public final class ContentType {
  public static final String CONTENT_TYPE = "Content-Type";
  public static final String APPLICATION_JSON = "application/json; charset=UTF-8";
  public static final String TEXT_PLAIN = "text/plain; ISO_8859_1";

  private ContentType() {
    throw new UnsupportedOperationException("Can't instantiate utility class");
  }
}
