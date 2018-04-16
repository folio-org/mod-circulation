package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.joda.time.DateTime;

public class User extends JsonObject {
  public User(JsonObject representation) {
    super(representation.getMap());
  }

  public Boolean isInactive() {
    return !isActive();
  }

  public Boolean isActive() {
    final Boolean active = getBoolean("active");

    return active && !isExpired();
  }

  private Boolean isExpired() {
    if(containsKey("expirationDate")) {
      final DateTime expirationDate = DateTime.parse(getString("expirationDate"));

      return expirationDate.isBefore(DateTime.now());
    }
    else {
      return false;
    }
  }
}
