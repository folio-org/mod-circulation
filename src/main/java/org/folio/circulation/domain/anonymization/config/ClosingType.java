package org.folio.circulation.domain.anonymization.config;

import java.util.Arrays;
import java.util.Objects;

/**
 * Enum for loan`s closing type representation.
 */
public enum ClosingType {

  IMMEDIATELY("immediately"),
  INTERVAL("interval"),
  NEVER("never"),
  UNKNOWN("Unknown");

  private String representation;

  ClosingType(String representation) {
    this.representation = representation;
  }

  public String getRepresentation() {
    return representation;
  }

  public static ClosingType from(String value) {
    return Arrays.stream(values())
        .filter(v -> Objects.equals(v.getRepresentation(), (value)))
        .findFirst()
        .orElse(UNKNOWN);
  }
}
