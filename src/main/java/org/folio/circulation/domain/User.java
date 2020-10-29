package org.folio.circulation.domain;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.folio.circulation.support.json.JsonObjectArrayPropertyFetcher.toStream;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getNestedStringProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getObjectProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import java.util.Objects;

import org.joda.time.DateTime;

import io.vertx.core.json.JsonObject;
import lombok.val;

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

  public boolean isInactive() {
    return !isActive();
  }

  boolean isActive() {
    final boolean active = getBooleanProperty(representation,"active");

    return active && !isExpired();
  }

  private boolean isExpired() {
    val expirationDate = getDateTimeProperty(representation, "expirationDate", null);

    if (expirationDate == null) {
      return false;
    }
    else {
      return expirationDate.isBefore(DateTime.now());
    }
  }

  public DateTime getExpirationDate() {
    return getDateTimeProperty(representation, "expirationDate", null);
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

  public JsonObject getAddressByType(String type) {
    JsonObject personal = getObjectProperty(representation, PERSONAL_PROPERTY_NAME);

    val addresses = toStream(personal, "addresses");

    return addresses
      .filter(Objects::nonNull)
      .filter(address -> Objects.equals(getProperty(address, "addressTypeId"), type))
      .findFirst()
      .orElse(null);
  }

  public PatronGroup getPatronGroup() {
    return patronGroup;
  }

  public String getPersonalName() {
    if (isNotBlank(getFirstName()) && isNotBlank(getLastName())) {
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
