package org.folio.circulation.domain.notice.session;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

public enum PatronActionType {

  CHECK_OUT("Check-out"),
  CHECK_IN("Check-in"),
  ALL("");

  private String representation;

  PatronActionType(String representation) {
    this.representation = representation;
  }

  public String getRepresentation() {
    return representation;
  }

  public static Optional<PatronActionType> from(String value) {
    return Arrays.stream(values())
      .filter(type -> Objects.equals(type.getRepresentation(), value))
      .findFirst();
  }
}
