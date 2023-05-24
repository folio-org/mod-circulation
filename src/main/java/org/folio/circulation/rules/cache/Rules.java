package org.folio.circulation.rules.cache;

import org.folio.circulation.rules.Drools;

public class Rules {

  public volatile String rulesAsText = "";
  public volatile String rulesAsDrools = "";
  public volatile Drools drools;
  /** System.currentTimeMillis() of the last load/reload of the rules from the storage */
  public volatile long reloadTimestamp;
}
