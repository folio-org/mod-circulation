package org.folio.circulation.support.http.server;

public class ValidationError {

  private final String propertyName;

  public ValidationError(String propertyName) {

    this.propertyName = propertyName;
  }

  public String getPropertyName() {
    return propertyName;
  }
}
