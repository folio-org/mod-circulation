package org.folio.circulation.domain.notice.session;

import java.util.Arrays;
import java.util.Objects;

public enum PatronActionType {

  CHECK_OUT("Check-out"),
  CHECK_IN("Check-in");

  private String representation;

  PatronActionType(String representation) {
    this.representation = representation;
  }

  public String getRepresentation() {
    return representation;
  }

  public static PatronActionType from(String value) {
    return Arrays.stream(values())
      .filter(v -> Objects.equals(v.getRepresentation(), (value)))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("Invalid patron action type representation: " + value));
  }
}
