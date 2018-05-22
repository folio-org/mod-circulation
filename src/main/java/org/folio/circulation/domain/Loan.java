package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;

public class Loan {
  private final JsonObject representation;

  public Loan(JsonObject representation) {
    this.representation = representation;
  }

  public static Loan from(JsonObject representation) {
    return new Loan(representation);
  }

  public JsonObject asJson() {
    return representation.copy();
  }

  String getUserId() {
    return representation.getString("userId");
  }

  String getProxyUserId() {
    return representation.getString("proxyUserId");
  }

  public String getString(String propertyName) {
    return representation.getString(propertyName);
  }

  public boolean containsKey(String propertyName) {
    return representation.containsKey(propertyName);
  }

  public JsonObject getJsonObject(String propertyName) {
    return representation.getJsonObject(propertyName);
  }

  public void put(String propertyName, String value) {
    representation.put(propertyName, value);
  }
}
