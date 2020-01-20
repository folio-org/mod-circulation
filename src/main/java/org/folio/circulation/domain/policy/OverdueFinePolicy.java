package org.folio.circulation.domain.policy;

import io.vertx.core.json.JsonObject;

import static org.folio.circulation.support.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

public class OverdueFinePolicy extends Policy {
  private Boolean ignoreGracePeriodForRecalls;
  private Boolean countClosed;

  private OverdueFinePolicy(String id) {
    this(id, null, null, null);
  }

  private OverdueFinePolicy(
    String id, String name, Boolean ignoreGracePeriodForRecalls, Boolean countClosed) {
    super(id, name);
    this.ignoreGracePeriodForRecalls = ignoreGracePeriodForRecalls;
    this.countClosed = countClosed;
  }

  public static OverdueFinePolicy from(JsonObject json) {
    return new OverdueFinePolicy(
      getProperty(json, "id"),
      getProperty(json, "name"),
      getBooleanProperty(json, "gracePeriodRecall"),
      getBooleanProperty(json, "countClosed")
    );
  }

  public Boolean getIgnoreGracePeriodForRecalls() {
    return ignoreGracePeriodForRecalls;
  }

  public Boolean getCountClosed() {
    return countClosed;
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
