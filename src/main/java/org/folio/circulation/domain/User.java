package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.joda.time.DateTime;

import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

public class User extends JsonObject {
  private final JsonObject representation;

  public User(JsonObject representation) {
    super(representation.getMap());

    this.representation = representation;
  }

  Boolean isInactive() {
    return !isActive();
  }

  Boolean isActive() {
    final Boolean active = representation.getBoolean("active");

    return active && !isExpired();
  }

  private Boolean isExpired() {
    if(representation.containsKey("expirationDate")) {
      final DateTime expirationDate = DateTime.parse(
        representation.getString("expirationDate"));

      return expirationDate.isBefore(DateTime.now());
    }
    else {
      return false;
    }
  }

  public String getBarcode() {
    return getProperty(representation, "barcode");
  }
}
