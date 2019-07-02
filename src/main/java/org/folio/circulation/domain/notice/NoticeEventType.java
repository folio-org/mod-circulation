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
  RECALL_TO_LOANEE("Recall loanee"),
  REQUEST_CANCELLATION("Request cancellation"),
  AVAILABLE("Available"),
  HOLD_EXPIRATION("Hold Expiration"),
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
