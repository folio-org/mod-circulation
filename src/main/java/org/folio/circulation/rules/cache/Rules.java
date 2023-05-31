package org.folio.circulation.rules.cache;

import org.folio.circulation.rules.Drools;

import lombok.Getter;
import lombok.AllArgsConstructor;

@Getter
@AllArgsConstructor
public class Rules {
  private final String rulesAsText;
  private final String rulesAsDrools;
  private final Drools drools;
  /** System.currentTimeMillis() of the last load/reload of the rules from the storage */
  private final long reloadTimestamp;

  public Rules() {
    rulesAsText = "";
    rulesAsDrools = "";
    drools = null;
    reloadTimestamp = 0;
  }
}
