package org.folio.circulation.domain.policy;

import io.vertx.core.json.JsonObject;

import static org.folio.circulation.support.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

public class OverdueFinePolicy extends CirculationPolicy {
  private Boolean gracePeriodRecall;
  private Boolean countClosed;

  private OverdueFinePolicy(String id) {
    this(id, null);
  }

  private OverdueFinePolicy(String id, String name) {
    super(id, name);
  }

  public static OverdueFinePolicy from(JsonObject overdueFinePolicy) {
    return new OverdueFinePolicy(
      getProperty(overdueFinePolicy, "id"),
      getProperty(overdueFinePolicy, "name")
    );
  }

  public static OverdueFinePolicy unknown(String id) {
    return new OverdueFinePolicy.UnknownOverdueFinePolicy(id);
  }

  private static class UnknownOverdueFinePolicy extends OverdueFinePolicy {
    UnknownOverdueFinePolicy(String id) {
      super(id);
    }
  }
}
