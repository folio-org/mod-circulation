package org.folio.circulation.domain;

import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;

import java.util.Arrays;

public enum LossType {
  NONE(""),
  AGED_TO_LOST("Aged to lost"),
  DECLARED_LOST("Declared lost");

  public final String value;

  LossType(String value) {
    this.value = value;
  }

  public static LossType from(String value) {
    return Arrays.stream(values())
      .filter(status -> status.nameMatches(value))
      .findFirst()
      .orElse(NONE);
  }

  public String getValue() {
    return value;
  }

  public boolean nameMatches(String value) {
    return equalsIgnoreCase(getValue(), value);
  }
}
