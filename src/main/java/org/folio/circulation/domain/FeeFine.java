package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;

public class FeeFine {
  private String id;
  private String feeFineType;

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
