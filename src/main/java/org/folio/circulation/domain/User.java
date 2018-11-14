package org.folio.circulation.domain;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;

import static org.folio.circulation.support.JsonPropertyFetcher.getNestedStringProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.JsonPropertyWriter.write;

public class User {
  private static final String PERSONAL_PROPERTY_NAME = "personal";

  private final JsonObject representation;

  public User(JsonObject representation) {
    this.representation = representation;
  }

  public boolean canDetermineStatus() {
    return !representation.containsKey("active");
  }

  public Boolean isInactive() {
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

  public String getId() {
    return getProperty(representation, "id");
  }

  public String getUsername() {
    return getProperty(representation, "username");
  }

  public String getPatronGroup() {
    return getProperty(representation, "patronGroup");
  }

  public String getLastName() {
    return getNestedStringProperty(representation, PERSONAL_PROPERTY_NAME, "lastName");
  }

  public String getFirstName() {
    return getNestedStringProperty(representation, PERSONAL_PROPERTY_NAME, "firstName");
  }

  public String getMiddleName() {
    return getNestedStringProperty(representation, PERSONAL_PROPERTY_NAME, "middleName");
  }
  
  public JsonObject getAddressByType(String type) {
    JsonObject personal =representation.getJsonObject(PERSONAL_PROPERTY_NAME);
    if(personal == null) { return null; }
    JsonArray addresses = personal.getJsonArray("addresses");
    if(addresses == null) { return null; }
    for(Object ob : addresses) {
      JsonObject address = (JsonObject)ob;
      if(address != null && address.getString("type").equals(type)) {
        return address;
      }
    }
    return null;
  }

  public JsonObject createUserSummary() {
    //TODO: Extract to visitor based adapter
    JsonObject userSummary = new JsonObject();

    write(userSummary, "lastName", getLastName());
    write(userSummary, "firstName", getFirstName());
    write(userSummary, "middleName", getMiddleName());
    write(userSummary, "barcode", getBarcode());

    return userSummary;
  }

  public String getPersonalName() {
    if(StringUtils.isNotBlank(getFirstName()) &&
      StringUtils.isNotBlank(getLastName())) {

      return String.format("%s, %s", getLastName(),
        getFirstName());
    }
    else {
      //Fallback to user name if insufficient personal details
      return getUsername();
    }
  }
}
