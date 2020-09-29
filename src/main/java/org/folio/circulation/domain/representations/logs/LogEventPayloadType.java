package org.folio.circulation.domain.representations.logs;

public enum LogEventPayloadType {
  NOTICE("NOTICE");

  private final String value;

  LogEventPayloadType(String value) {
    this.value = value;
  }

  public String value() {
    return this.value;
  }
}
