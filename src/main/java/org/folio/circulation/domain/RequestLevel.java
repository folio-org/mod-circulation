package org.folio.circulation.domain;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;

public enum RequestLevel {
  NONE(""),
  ITEM("Item"),
  TITLE("Title");

  public final String value;

  RequestLevel(String value) {
    this.value = value;
  }

  public static RequestLevel from(String value) {
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

  public static String invalidRequestLevelErrorMessage() {
    return "requestLevel must be one of the following: " +
      Arrays.stream(values())
        .map(requestLevel -> StringUtils.wrap(requestLevel.value, '"'))
        .collect(Collectors.joining(", "));
  }
}
