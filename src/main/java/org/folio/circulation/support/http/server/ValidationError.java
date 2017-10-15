package org.folio.circulation.support.http.server;

public class ValidationError {

  public final String propertyName;
  public final String value;

  public ValidationError(String propertyName, String value) {
    this.propertyName = propertyName;
    this.value = value;
  }
}
