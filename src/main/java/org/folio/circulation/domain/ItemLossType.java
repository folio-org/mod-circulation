package org.folio.circulation.domain;

import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;

import java.util.Arrays;

public enum ItemLossType {
  NONE(""),
  AGED_TO_LOST("Aged to lost"),
  DECLARED_LOST("Declared lost");

  public final String value;

  ItemLossType(String value) {
    this.value = value;
  }

  public static ItemLossType from(String value) {
    return Arrays.stream(values())
      .filter(status -> status.valueMatches(value))
      .findFirst()
      .orElse(NONE);
  }

  public String getValue() {
    return value;
  }

  public boolean valueMatches(String value) {
    return equalsIgnoreCase(getValue(), value);
  }
}
