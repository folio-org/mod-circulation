package org.folio.circulation.domain.notice;

import java.util.Arrays;
import java.util.Objects;

public enum NoticeEventType {

  CHECK_IN("Check in"),
  CHECK_OUT("Check out"),
  RENEWED("Renewed"),
  DUE_DATE("Due date"),

  PAGING_REQUEST("Paging request"),
  HOLD_REQUEST("Hold request"),
  RECALL_REQUEST("Recall request"),
  ITEM_RECALLED("Item recalled"),
  REQUEST_CANCELLATION("Request cancellation"),
  AVAILABLE("Available"),
  REQUEST_EXPIRATION("Request expiration"),
  HOLD_EXPIRATION("Hold expiration"),
  UNKNOWN("Unknown");


  private String representation;

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
