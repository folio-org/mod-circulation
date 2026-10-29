package org.folio.circulation.domain.anonymization.config;

import java.util.Arrays;
import java.util.Objects;

/**
 * The "Opt-in loan anonymization policy" radio group, stored in {@code closingType.userSpecific}
 * of the {@code loan_history} setting.
 * <p>
 * Not {@link ClosingType}: there {@code never} means "never anonymize", not "off".
 */
public enum UserSpecificClosingType {
  DISABLED("disabled"),
  IMMEDIATELY("immediately"),
  INTERVAL("interval"),
  UNKNOWN("Unknown");

  private final String representation;

  UserSpecificClosingType(String representation) {
    this.representation = representation;
  }

  public String getRepresentation() {
    return representation;
  }

  public static UserSpecificClosingType from(String value) {
    if (value == null) {
      return DISABLED;
    }

    return Arrays.stream(values())
        .filter(v -> Objects.equals(v.getRepresentation(), value))
        .findFirst()
        .orElse(UNKNOWN);
  }
}
