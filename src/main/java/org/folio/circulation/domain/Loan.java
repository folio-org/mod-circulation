package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;

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

  public void changeDueDate(DateTime dueDate) {
    representation.put("dueDate",
      dueDate.toString(ISODateTimeFormat.dateTime()));
  }

  public void changeUser(String userId) {
    representation.put("userId", userId);
  }

  public void changeItem(String itemId) {
    representation.put("itemId", itemId);
  }

  public void changeProxyUser(String userId) {
    representation.put("proxyUserId", userId);
  }
}
