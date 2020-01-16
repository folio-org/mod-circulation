package org.folio.circulation.domain.policy;

import io.vertx.core.json.JsonObject;

import static org.folio.circulation.support.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

public class OverdueFinePolicy extends CirculationPolicy {
  private Boolean ignoreGracePeriodForRecalls;
  private Boolean countClosed;

  private OverdueFinePolicy(String id) {
    this(id, null);
  }

  private OverdueFinePolicy(String id, String name) {
    super(id, name);
  }

  public static OverdueFinePolicy from(JsonObject json) {
    OverdueFinePolicy overdueFinePolicy = new OverdueFinePolicy(
      getProperty(json, "id"),
      getProperty(json, "name")
    );

    overdueFinePolicy.setCountClosed(
      getBooleanProperty(json, "countClosed"));
    overdueFinePolicy.setIgnoreGracePeriodForRecalls(
      getBooleanProperty(json, "gracePeriodRecall"));

    return overdueFinePolicy;
  }

  public Boolean getIgnoreGracePeriodForRecalls() {
    return ignoreGracePeriodForRecalls;
  }

  public void setIgnoreGracePeriodForRecalls(Boolean ignoreGracePeriodForRecalls) {
    this.ignoreGracePeriodForRecalls = ignoreGracePeriodForRecalls;
  }

  public Boolean getCountClosed() {
    return countClosed;
  }

  public void setCountClosed(Boolean countClosed) {
    this.countClosed = countClosed;
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
