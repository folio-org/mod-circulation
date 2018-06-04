package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.joda.time.DateTime;

import static org.folio.circulation.support.JsonPropertyCopier.copyStringIfExists;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

public class User {
  private final JsonObject representation;

  public User(JsonObject representation) {
    this.representation = representation;
  }

  boolean canDetermineStatus() {
    return !representation.containsKey("active");
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

  public JsonObject asJson() {
    return representation.copy();
  }

  public String getId() {
    return getProperty(representation, "id");
  }

  public String getPatronGroup() {
    return getProperty(representation, "patronGroup");
  }

  public JsonObject createUserSummary() {
    //TODO: Extract to visitor based adapter
    JsonObject userSummary = new JsonObject();

    if(representation.containsKey("personal")) {
      JsonObject personalDetails = representation.getJsonObject("personal");

      copyStringIfExists("lastName", personalDetails, userSummary);
      copyStringIfExists("firstName", personalDetails, userSummary);
      copyStringIfExists("middleName", personalDetails, userSummary);
    }

    copyStringIfExists("barcode", representation, userSummary);

    return userSummary;
  }
}
