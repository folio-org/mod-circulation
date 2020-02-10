package org.folio.circulation.domain.policy;

import io.vertx.core.json.JsonObject;

import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

public class OverdueFinePolicy extends Policy {
  private Boolean ignoreGracePeriodForRecalls;
  private Boolean countPeriodsWhenServicePointIsClosed;

  private OverdueFinePolicy(
    String id, String name, Boolean ignoreGracePeriodForRecalls, Boolean countPeriodsWhenServicePointIsClosed) {
    super(id, name);
    this.ignoreGracePeriodForRecalls = ignoreGracePeriodForRecalls;
    this.countPeriodsWhenServicePointIsClosed = countPeriodsWhenServicePointIsClosed;
  }

  public static OverdueFinePolicy from(JsonObject json) {
    return new OverdueFinePolicy(
      getProperty(json, "id"),
      getProperty(json, "name"),
      json.getBoolean("gracePeriodRecall"),
      json.getBoolean("countClosed")
    );
  }

  public Boolean getIgnoreGracePeriodForRecalls() {
    return ignoreGracePeriodForRecalls;
  }

  public Boolean getCountPeriodsWhenServicePointIsClosed() {
    return countPeriodsWhenServicePointIsClosed;
  }

  public static OverdueFinePolicy unknown(String id) {
    return new OverdueFinePolicy.UnknownOverdueFinePolicy(id);
  }

  private static class UnknownOverdueFinePolicy extends OverdueFinePolicy {
    UnknownOverdueFinePolicy(String id) {
      super(id, null, null, null);
    }
  }
}
