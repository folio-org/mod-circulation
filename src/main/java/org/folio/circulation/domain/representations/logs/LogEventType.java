package org.folio.circulation.domain.representations.logs;

public enum LogEventType {
  LOAN("LOAN"),
  NOTICE("NOTICE"),
  NOTICE_ERROR("NOTICE_ERROR"),
  CHECK_IN("CHECK_IN_EVENT"),
  CHECK_OUT("CHECK_OUT_EVENT"),
  CHECK_OUT_THROUGH_OVERRIDE("CHECK_OUT_THROUGH_OVERRIDE_EVENT"),
  REQUEST_CREATED("REQUEST_CREATED_EVENT"),
  REQUEST_CREATED_THROUGH_OVERRIDE("REQUEST_CREATED_THROUGH_OVERRIDE_EVENT"),
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
