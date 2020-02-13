package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;

public class FeeFine {
  public static final String OVERDUE_FINE_TYPE = "Overdue fine";

  private final String id;
  private final String feeFineType;

  public FeeFine(String id, String feeFineType) {
    this.id = id;
    this.feeFineType = feeFineType;
  }

  public static FeeFine from(JsonObject jsonObject) {
    return new FeeFine(jsonObject.getString("id"), jsonObject.getString("feeFineType"));
  }

  public String getId() {
    return id;
  }

  public String getFeeFineType() {
    return feeFineType;
  }
}
