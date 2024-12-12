package org.folio.circulation.domain;

import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;

import java.util.Arrays;

public enum EcsRequestPhase {
  NONE(""),
  PRIMARY("Primary"),
  SECONDARY("Secondary"),
  INTERMEDIATE("Intermediate");

  public final String value;

  public static EcsRequestPhase from(String value) {
    return Arrays.stream(values())
      .filter(status -> status.nameMatches(value))
      .findFirst()
      .orElse(NONE);
  }

  EcsRequestPhase(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public boolean nameMatches(String value) {
    return equalsIgnoreCase(getValue(), value);
  }

  @Override
  public String toString() {
    return value;
  }
}
