package org.folio.circulation.domain.policy;

import io.vertx.core.json.JsonObject;

import static org.folio.circulation.support.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

public class OverdueFinePolicy extends CirculationPolicy {
  private Boolean shouldIgnoreGracePeriodsForRecalls;
  private Boolean shouldCountClosed;

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

    overdueFinePolicy.setShouldCountClosed(
      getBooleanProperty(json, "countClosed"));
    overdueFinePolicy.setShouldIgnoreGracePeriodsForRecalls(
      getBooleanProperty(json, "gracePeriodRecall"));

    return overdueFinePolicy;
  }

  public Boolean getShouldIgnoreGracePeriodsForRecalls() {
    return shouldIgnoreGracePeriodsForRecalls;
  }

  public void setShouldIgnoreGracePeriodsForRecalls(Boolean shouldIgnoreGracePeriodsForRecalls) {
    this.shouldIgnoreGracePeriodsForRecalls = shouldIgnoreGracePeriodsForRecalls;
  }

  public Boolean getShouldCountClosed() {
    return shouldCountClosed;
  }

  public void setShouldCountClosed(Boolean shouldCountClosed) {
    this.shouldCountClosed = shouldCountClosed;
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
