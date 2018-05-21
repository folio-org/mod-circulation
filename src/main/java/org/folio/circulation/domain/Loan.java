package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;

public class Loan extends JsonObject {
  public Loan(JsonObject representation) {
    super(representation.getMap());
  }

  public static Loan from(JsonObject representation) {
    return new Loan(representation);
  }

  String getUserId() {
    return getString("userId");
  }

  String getProxyUserId() {
    return getString("proxyUserId");
  }
}
