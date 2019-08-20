package org.folio.circulation.domain.notice.schedule;

import java.util.Arrays;
import java.util.Objects;

public enum  TriggeringEvent {

  HOLD_EXPIRATION("Hold expiration"),
  REQUEST_EXPIRATION("Request expiration");

  private String representation;

  TriggeringEvent(String representation) {
    this.representation = representation;
  }

  public String getRepresentation() {
    return representation;
  }

  public static TriggeringEvent from(String value) {
    return Arrays.stream(values())
      .filter(v -> Objects.equals(v.getRepresentation(), (value)))
      .findFirst()
      .orElse(null);
  }
}
