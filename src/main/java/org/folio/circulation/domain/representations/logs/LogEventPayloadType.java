package org.folio.circulation.domain.representations.logs;

public enum LogEventPayloadType {
  NOTICE("NOTICE"),
  CHECK_IN("CHECK_IN_EVENT"),
  CHECK_OUT("CHECK_OUT_EVENT");

  private final String value;

  LogEventPayloadType(String value) {
    this.value = value;
  }

  public String value() {
    return this.value;
  }
}
