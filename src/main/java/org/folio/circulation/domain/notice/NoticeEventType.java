package org.folio.circulation.domain.notice;

import java.util.Arrays;
import java.util.Objects;

public enum NoticeEventType {

  // Loan notices
  CHECK_IN("Check in"),
  CHECK_OUT("Check out"),
  RENEWED("Renewed"),
  MANUAL_DUE_DATE_CHANGE("Manual due date change"),
  DUE_DATE("Due date"),
  ITEM_RECALLED("Item recalled"),
  AGED_TO_LOST("Aged to lost"),
  HOLD_REQUEST_FOR_ITEM("Hold request for item"),

  // Request notices,
  PAGING_REQUEST("Paging request"),
  HOLD_REQUEST("Hold request"),
  RECALL_REQUEST("Recall request"),
  REQUEST_CANCELLATION("Request cancellation"),
  AVAILABLE("Available"),
  REQUEST_EXPIRATION("Request expiration"),
  TITLE_LEVEL_REQUEST_EXPIRATION("Title level request expiration"),
  HOLD_EXPIRATION("Hold expiration"),

  // Fee/fine notices
  OVERDUE_FINE_RETURNED("Overdue fine returned"),
  OVERDUE_FINE_RENEWED("Overdue fine renewed"),
  AGED_TO_LOST_FINE_CHARGED("Aged to lost - fine charged"),
  AGED_TO_LOST_RETURNED("Aged to lost & item returned - fine adjusted"),

  UNKNOWN("Unknown");


  private final String representation;

  NoticeEventType(String representation) {
    this.representation = representation;
  }

  public String getRepresentation() {
    return representation;
  }

  public static NoticeEventType from(String value) {
    return Arrays.stream(values())
      .filter(v -> Objects.equals(v.getRepresentation(), (value)))
      .findFirst()
      .orElse(UNKNOWN);
  }
}
