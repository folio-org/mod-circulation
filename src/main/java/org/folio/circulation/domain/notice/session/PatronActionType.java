package org.folio.circulation.domain.notice.session;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

public enum PatronActionType {

  CHECK_OUT("Check-out"),
  CHECK_IN("Check-in"),
  UNKNOWN("");

  private String representation;

  PatronActionType(String representation) {
    this.representation = representation;
  }

  public String getRepresentation() {
    return representation;
  }

  public boolean isValid() {
    return this != UNKNOWN;
  }

  public static PatronActionType from(String value) {
    return Arrays.stream(values())
      .filter(type -> Objects.equals(type.getRepresentation(), value))
      .findFirst()
      .orElse(UNKNOWN);
  }

  public static String invalidActionTypeErrorMessage() {
    String joinedRepresentations = Arrays.stream(values())
      .filter(PatronActionType::isValid)
      .map(type -> type.representation)
      .collect(Collectors.joining(", "));

    return "Patron action type must be " + joinedRepresentations;
  }
}
