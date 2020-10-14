package org.folio.circulation.domain.representations.logs;

public enum LogEventType {
  LOAN("LOAN"),
  NOTICE("NOTICE"),
  CHECK_IN("CHECK_IN_EVENT"),
  CHECK_OUT("CHECK_OUT_EVENT"),
  REQUEST_CREATED("REQUEST_CREATED_EVENT"),
  REQUEST_UPDATED("REQUEST_UPDATED_EVENT"),
  REQUEST_MOVED("REQUEST_MOVED_EVENT"),
  REQUEST_REORDERED("REQUEST_REORDERED_EVENT");

  private final String value;

  LogEventType(String value) {
    this.value = value;
  }

  public String value() {
    return this.value;
  }
}
