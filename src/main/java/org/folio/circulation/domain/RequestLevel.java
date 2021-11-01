package org.folio.circulation.domain;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

public enum RequestLevel {
  ITEM("Item");

  private final String value;

  RequestLevel(String value) {
    this.value = value;
  }

  public static String invalidRequestLevelErrorMessage() {
    return "requestLevel must be one of the following: " +
      Arrays.stream(values())
        .map(requestLevel -> StringUtils.wrap(requestLevel.value, '"'))
        .collect(Collectors.joining(", "));
  }

  public String value() {
    return this.value;
  }

  @Override
  public String toString() {
    return this.value;
  }
}
