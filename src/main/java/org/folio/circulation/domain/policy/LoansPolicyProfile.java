package org.folio.circulation.domain.policy;

import java.util.Arrays;
import java.util.function.Predicate;

public enum LoansPolicyProfile {

  ROLLING, FIXED, INCORRECT;

  public static LoansPolicyProfile getProfileByName(String name) {
    return Arrays.stream(values())
      .filter(predicate(name))
      .findFirst()
      .orElse(INCORRECT);
  }

  private static Predicate<LoansPolicyProfile> predicate(String name) {
    return en -> en.name().equalsIgnoreCase(name);
  }
}
