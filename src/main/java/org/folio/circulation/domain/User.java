package org.folio.circulation.domain;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.folio.circulation.support.json.JsonObjectArrayPropertyFetcher.toStream;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getArrayProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getNestedStringProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getObjectProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.utils.DateTimeUtil.isBeforeMillis;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import io.vertx.core.json.JsonArray;
import org.folio.circulation.support.utils.ClockUtil;

import io.vertx.core.json.JsonObject;
import lombok.ToString;
import lombok.val;

@ToString(onlyExplicitlyIncluded = true)
public class User {
  private static final String PERSONAL_PROPERTY_NAME = "personal";
  private static final String ADDRESSES_PROPERTY_NAME = "addresses";
  private static final String USER_TYPE_PROPERTY_NAME = "type";
  private static final String DCB_USER_TYPE = "dcb";

  private final PatronGroup patronGroup;
  private final Collection<Department> departments;

  @ToString.Include
  private final JsonObject representation;

  public User(JsonObject representation) {
    this(representation, null, null);
  }

  public User(JsonObject representation, PatronGroup patronGroup, Collection<Department> departments) {
    this.representation = representation;
    this.patronGroup = patronGroup;
    this.departments = departments;
  }

  public User withPatronGroup(PatronGroup newPatronGroup) {
    return new User(representation, newPatronGroup, departments);
  }

  public User withDepartments(Collection<Department> departments) {
    return new User(representation, patronGroup, departments);
  }

  public boolean cannotDetermineStatus() {
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
      return isBeforeMillis(expirationDate, ClockUtil.getZonedDateTime());
    }
  }

  public ZonedDateTime getExpirationDate() {
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

  public String getPreferredFirstName() {
    return getNestedStringProperty(representation, PERSONAL_PROPERTY_NAME, "preferredFirstName");
  }

  public String getMiddleName() {
    return getNestedStringProperty(representation, PERSONAL_PROPERTY_NAME, "middleName");
  }

  public JsonArray getAddresses() {
    JsonArray addresses = getPersonal() == null ? null : getPersonal().getJsonArray(ADDRESSES_PROPERTY_NAME);
    return addresses == null ? new JsonArray() : addresses;
  }

  public JsonObject getAddressByType(String type) {
    JsonObject personal = getObjectProperty(representation, PERSONAL_PROPERTY_NAME);

    val addresses = toStream(personal, ADDRESSES_PROPERTY_NAME);

    return addresses
      .filter(Objects::nonNull)
      .filter(address -> Objects.equals(getProperty(address, "addressTypeId"), type))
      .findFirst()
      .orElse(null);
  }

  public JsonObject getPrimaryAddress() {
    JsonObject personal = getObjectProperty(representation, PERSONAL_PROPERTY_NAME);
    val addresses = toStream(personal, ADDRESSES_PROPERTY_NAME);
    return addresses
      .filter(Objects::nonNull)
      .filter(address -> Objects.equals(getBooleanProperty(address, "primaryAddress"), true))
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

  public JsonObject getPersonal() {
    return getObjectProperty(representation,PERSONAL_PROPERTY_NAME);
  }

  public List<String> getDepartmentIds() {
    return getArrayProperty(representation, "departments")
      .stream()
      .map(String.class::cast)
      .collect(Collectors.toList());
  }

  public Collection<Department> getDepartments() {
    return departments;
  }

  public boolean isDcbUser() {
    return DCB_USER_TYPE.equals(getProperty(representation, USER_TYPE_PROPERTY_NAME));
  }

}
