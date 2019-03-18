package org.folio.circulation.domain.notice;

import java.util.Arrays;

public enum NoticeTiming {

  UPON_AT("Upon At", false),
  BEFORE("Before", true),
  AFTER("After", true);

  private String representation;
  private boolean requiresPeriod;

  NoticeTiming(String representation, boolean supportsPeriod) {
    this.representation = representation;
    this.requiresPeriod = supportsPeriod;
  }

  public String getRepresentation() {
    return representation;
  }

  public static NoticeTiming from(String value) {
    return Arrays.stream(values())
      .filter(v -> v.getRepresentation().equals(value))
      .findFirst()
      .orElse(UPON_AT);
  }

  public boolean requiresPeriod() {
    return requiresPeriod;
  }
}
