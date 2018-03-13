package org.folio.circulation.support;

public class InvalidOkapiLocationException extends RuntimeException {
  public InvalidOkapiLocationException(String okapiLocation, Exception cause) {
      super(String.format("Invalid Okapi URL: %s", okapiLocation), cause);
  }
}
