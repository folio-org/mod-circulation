package org.folio.circulation.domain;

import static org.folio.circulation.support.JsonPropertyFetcher.getNestedStringProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.JsonPropertyWriter.write;

import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class User {
  private static final String PERSONAL_PROPERTY_NAME = "personal";
  private final PatronGroup patronGroup;

  private final JsonObject representation;

  public User(JsonObject representation) {
    this(representation, null);    
  }
  
  public User(JsonObject representation, PatronGroup patronGroup) {
    this.representation = representation;
    this.patronGroup = patronGroup;
  }
  
  public User withPatronGroup(PatronGroup newPatronGroup) {
    return new User(representation, newPatronGroup);
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

  public String getPatronGroupId() {
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
  
  JsonObject getAddressByType(String type) {
    JsonObject personal = representation.getJsonObject(PERSONAL_PROPERTY_NAME);

    if(personal == null) { return null; }

    JsonArray addresses = personal.getJsonArray("addresses");

    if(addresses == null) { return null; }

    for(Object ob : addresses) {
      JsonObject address = (JsonObject)ob;
      if (address != null
        && Objects.equals(address.getString("addressTypeId"), (type))) {

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
    
    if(patronGroup != null) {
      JsonObject patronGroupSummary = new JsonObject();
      write(patronGroupSummary, "id", patronGroup.getId());
      write(patronGroupSummary, "group", patronGroup.getGroup());
      write(patronGroupSummary, "desc", patronGroup.getDesc());
      userSummary.put("patronGroup", patronGroupSummary);
    }
    
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
  
  public static User from(JsonObject representation) {
    return new User(representation);
  }
}
