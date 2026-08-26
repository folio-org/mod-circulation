package api.support.builders;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import api.support.http.IndividualResource;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.ObjectUtils;

public class UserBuilder extends JsonBuilder implements Builder {
  private final UUID id;
  private final String username;
  private final String lastName;
  private final String firstName;
  private final String middleName;
  private final String preferredFirstName;
  private final String barcode;
  private final UUID patronGroupId;
  private final Boolean active;
  private final ZonedDateTime expirationDate;
  private final Collection<Address> addresses;
  private final JsonArray departments;
  private final JsonArray preferredContactTypeIds;
  private final String preferredContactTypeId;
  private final String type;
  private final boolean primaryAddress;

  public UserBuilder() {
    this(UUID.randomUUID(), "sjones", "Jones", "Steven", null, null,"785493025613",
      null, true, null, new ArrayList<>(), null, null, null, null, false);
  }

  private UserBuilder(
    UUID id,
    String username,
    String lastName,
    String firstName,
    String middleName,
    String preferredFirstName,
    String barcode,
    UUID patronGroupId,
    Boolean active,
    ZonedDateTime expirationDate,
    Collection<Address> addresses,
    JsonArray departments,
    JsonArray preferredContactTypeIds,
    String preferredContactTypeId,
    String type,
    boolean primaryAddress) {

    this.id = id;
    this.username = username;

    this.lastName = lastName;
    this.middleName = middleName;
    this.preferredFirstName = preferredFirstName;
    this.firstName = firstName;

    this.barcode = barcode;
    this.patronGroupId = patronGroupId;
    this.active = active;
    this.expirationDate = expirationDate;

    this.addresses = addresses;
    this.departments = departments;
    this.preferredContactTypeIds = preferredContactTypeIds;
    this.preferredContactTypeId = preferredContactTypeId;
    this.type = type;
    this.primaryAddress = primaryAddress;
  }

  public JsonObject create() {
    JsonObject request = new JsonObject();

    if(this.id != null) {
      request.put("id", this.id.toString());
    }

    request.put("username", this.username);

    if(this.barcode != null) {
      request.put("barcode", this.barcode);
    }

    if(this.patronGroupId != null) {
      request.put("patronGroup", this.patronGroupId.toString());
    }

    put(request, "active", active);
    put(request, "expirationDate", expirationDate);

    if (ObjectUtils.anyNotNull(firstName, lastName, preferredContactTypeIds, preferredContactTypeId)) {
      JsonObject personalInformation = new JsonObject()
        .put("lastName", this.lastName)
        .put("firstName", this.firstName);

      if(this.middleName != null) {
        personalInformation.put("middleName", this.middleName);
      }

      if(this.preferredFirstName != null) {
        personalInformation.put("preferredFirstName", this.preferredFirstName);
      }

      if (this.preferredContactTypeIds != null) {
        personalInformation.put("preferredContactTypeIds", this.preferredContactTypeIds);
      }

      if (this.preferredContactTypeId != null) {
        personalInformation.put("preferredContactTypeId", this.preferredContactTypeId);
      }

      if(this.addresses != null && !this.addresses.isEmpty()) {
        JsonArray mappedAddresses = new JsonArray();

        this.addresses.forEach(address -> {
          final JsonObject mappedAddress = new JsonObject();

          put(mappedAddress, "addressTypeId", address.getType());
          put(mappedAddress, "addressLine1", address.getAddressLineOne());
          put(mappedAddress, "addressLine2", address.getAddressLineTwo());
          put(mappedAddress, "city", address.getCity());
          put(mappedAddress, "region", address.getRegion());
          put(mappedAddress, "postalCode", address.getPostalCode());
          put(mappedAddress, "countryId", address.getCountryId());
          put(mappedAddress, "primaryAddress", this.primaryAddress);

          mappedAddresses.add(mappedAddress);
        });

        personalInformation.put("addresses", mappedAddresses);
      }

      request.put("personal", personalInformation);
    }
    if (this.departments != null) {
      request.put("departments", this.departments);
    }
    if (this.type != null) {
      request.put("type", this.type);
    }

    return request;
  }

  public UserBuilder withName(String lastName, String firstName) {
    String name = firstName != null && !firstName.isEmpty() && lastName != null
      ? firstName.substring(0, 1).concat(lastName).toLowerCase()
      : null;

    return new UserBuilder(
      this.id,
      name,
      lastName,
      firstName,
      this.middleName,
      this.preferredFirstName,
      this.barcode,
      this.patronGroupId,
      this.active,
      this.expirationDate,
      this.addresses,
      this.departments,
      this.preferredContactTypeIds,
      this.preferredContactTypeId,
      this.type,
      this.primaryAddress);
  }

  public UserBuilder withName(String lastName, String firstName, String middleName) {
    return new UserBuilder(
      this.id,
      this.username,
      lastName,
      firstName,
      middleName,
      this.preferredFirstName,
      this.barcode,
      this.patronGroupId,
      this.active,
      this.expirationDate,
      this.addresses,
      this.departments,
      this.preferredContactTypeIds,
      this.preferredContactTypeId,
      this.type,
      this.primaryAddress);
  }

  public UserBuilder withPreferredFirstName(String lastName, String firstName,String preferredFirstName) {
    return new UserBuilder(
      this.id,
      firstName.substring(0, 1).concat(lastName).toLowerCase(),
      lastName,
      firstName,
      this.middleName,
      preferredFirstName,
      this.barcode,
      this.patronGroupId,
      this.active,
      this.expirationDate,
      this.addresses,
      this.departments,
      this.preferredContactTypeIds,
      this.preferredContactTypeId,
      this.type,
      this.primaryAddress);
  }

  public UserBuilder withPreferredFirstName(String lastName, String firstName,String middleName,String preferredFirstName) {
    return new UserBuilder(
      this.id,
      firstName.substring(0, 1).concat(lastName).toLowerCase(),
      lastName,
      firstName,
      middleName,
      preferredFirstName,
      this.barcode,
      this.patronGroupId,
      this.active,
      this.expirationDate,
      this.addresses,
      this.departments,
      this.preferredContactTypeIds,
      this.preferredContactTypeId,
      this.type,
      this.primaryAddress);
  }

  public UserBuilder withNoPersonalDetails() {
    return new UserBuilder(
      this.id,
      this.username,
      null,
      null,
      null,
      this.preferredFirstName,
      this.barcode,
      this.patronGroupId,
      this.active,
      this.expirationDate,
      this.addresses,
      this.departments,
      this.preferredContactTypeIds,
      this.preferredContactTypeId,
      this.type,
      this.primaryAddress);
  }

  public UserBuilder withBarcode(String barcode) {
    return new UserBuilder(
      this.id,
      this.username,
      this.lastName,
      this.firstName,
      this.middleName,
      this.preferredFirstName,
      barcode,
      this.patronGroupId,
      this.active,
      this.expirationDate,
      this.addresses,
      this.departments,
      this.preferredContactTypeIds,
      this.preferredContactTypeId,
      this.type,
      this.primaryAddress);
  }

  public UserBuilder withNoBarcode() {
    return new UserBuilder(
      this.id,
      this.username,
      this.lastName,
      this.firstName,
      this.middleName,
      this.preferredFirstName,
      null,
      this.patronGroupId,
      this.active,
      this.expirationDate,
      this.addresses,
      this.departments,
      this.preferredContactTypeIds,
      this.preferredContactTypeId,
      this.type,
      this.primaryAddress);
  }

  public UserBuilder withUsername(String username) {
    return new UserBuilder(
      this.id,
      username,
      this.lastName,
      this.firstName,
      this.middleName,
      this.preferredFirstName,
      this.barcode,
      this.patronGroupId,
      this.active,
      this.expirationDate,
      this.addresses,
      this.departments,
      this.preferredContactTypeIds,
      this.preferredContactTypeId,
      this.type,
      this.primaryAddress);
  }

  public UserBuilder inGroupFor(IndividualResource patronGroup) {
    return withPatronGroupId(patronGroup.getId());
  }

  public UserBuilder withPatronGroupId(UUID patronGroupId) {
    return new UserBuilder(
      this.id,
      this.username,
      this.lastName,
      this.firstName,
      this.middleName,
      this.preferredFirstName,
      this.barcode,
      patronGroupId,
      this.active,
      this.expirationDate,
      this.addresses,
      this.departments,
      this.preferredContactTypeIds,
      this.preferredContactTypeId,
      this.type,
      this.primaryAddress);
  }

  public UserBuilder active() {
    return withActive(true);
  }

  public UserBuilder inactive() {
    return withActive(false);
  }

  public UserBuilder neitherActiveOrInactive() {
    return withActive(null);
  }

  public UserBuilder withActive(Boolean active) {
    return new UserBuilder(
      this.id,
      this.username,
      this.lastName,
      this.firstName,
      this.middleName,
      this.preferredFirstName,
      this.barcode,
      this.patronGroupId,
      active,
      this.expirationDate,
      this.addresses,
      this.departments,
      this.preferredContactTypeIds,
      this.preferredContactTypeId,
      this.type,
      this.primaryAddress);
  }

  public UserBuilder expires(ZonedDateTime newExpirationDate) {
    return new UserBuilder(
      this.id,
      this.username,
      this.lastName,
      this.firstName,
      this.middleName,
      this.preferredFirstName,
      this.barcode,
      this.patronGroupId,
      this.active,
      newExpirationDate,
      this.addresses,
      this.departments,
      this.preferredContactTypeIds,
      this.preferredContactTypeId,
      this.type,
      this.primaryAddress);
  }

  public UserBuilder noExpiration() {
    return new UserBuilder(
      this.id,
      this.username,
      this.lastName,
      this.firstName,
      this.middleName,
      this.preferredFirstName,
      this.barcode,
      this.patronGroupId,
      this.active,
      null,
      this.addresses,
      this.departments,
      this.preferredContactTypeIds,
      this.preferredContactTypeId,
      this.type,
      this.primaryAddress);
  }

  public UserBuilder withAddress(Address address) {
    final ArrayList<Address> newAddresses = new ArrayList<>(
      new ArrayList<>(addresses));

    newAddresses.add(address);

    return withAddresses(newAddresses);
  }

  public UserBuilder withDepartments(JsonArray departments){
    return new UserBuilder(
      this.id,
      this.username,
      this.lastName,
      this.firstName,
      this.middleName,
      this.preferredFirstName,
      this.barcode,
      this.patronGroupId,
      this.active,
      this.expirationDate,
      this.addresses,
      departments,
      this.preferredContactTypeIds,
      this.preferredContactTypeId,
      this.type,
      this.primaryAddress);
  }

  public UserBuilder withPreferredContactTypeIds(List<String> preferredContactTypeIds) {
    JsonArray values = preferredContactTypeIds == null ? null : new JsonArray(preferredContactTypeIds);
    return new UserBuilder(
      this.id,
      this.username,
      this.lastName,
      this.firstName,
      this.middleName,
      this.preferredFirstName,
      this.barcode,
      this.patronGroupId,
      this.active,
      this.expirationDate,
      this.addresses,
      this.departments,
      values,
      this.preferredContactTypeId,
      this.type,
      this.primaryAddress);
  }

  public UserBuilder withDeprecatedPreferredContactTypeId(String preferredContactTypeId) {
    return new UserBuilder(
      this.id,
      this.username,
      this.lastName,
      this.firstName,
      this.middleName,
      this.preferredFirstName,
      this.barcode,
      this.patronGroupId,
      this.active,
      this.expirationDate,
      this.addresses,
      this.departments,
      this.preferredContactTypeIds,
      preferredContactTypeId,
      this.type,
      this.primaryAddress);
  }

  public UserBuilder withPrimaryAddress(String primaryAddress){
    return new UserBuilder(
      this.id,
      this.username,
      this.lastName,
      this.firstName,
      this.middleName,
      this.preferredFirstName,
      this.barcode,
      this.patronGroupId,
      this.active,
      this.expirationDate,
      this.addresses,
      this.departments,
      this.preferredContactTypeIds,
      this.preferredContactTypeId,
      this.type,
      Boolean.valueOf(primaryAddress));
  }
  public UserBuilder withNoAddresses() {
    return withAddresses(new ArrayList<>());
  }

  private UserBuilder withAddresses(ArrayList<Address> newAddresses) {
    return new UserBuilder(
      this.id,
      this.username,
      this.lastName,
      this.firstName,
      this.middleName,
      this.preferredFirstName,
      this.barcode,
      this.patronGroupId,
      this.active,
      this.expirationDate,
      newAddresses,
      this.departments,
      this.preferredContactTypeIds,
      this.preferredContactTypeId,
      this.type,
      this.primaryAddress);
  }

  public UserBuilder withId(String id) {
    return new UserBuilder(
      UUID.fromString(id),
      this.username,
      this.lastName,
      this.firstName,
      this.middleName,
      this.preferredFirstName,
      this.barcode,
      this.patronGroupId,
      this.active,
      this.expirationDate,
      this.addresses,
      this.departments,
      this.preferredContactTypeIds,
      this.preferredContactTypeId,
      this.type,
      this.primaryAddress);
  }

  public UserBuilder withType(String type) {
    return new UserBuilder(
      this.id,
      this.username,
      this.lastName,
      this.firstName,
      this.middleName,
      this.preferredFirstName,
      this.barcode,
      this.patronGroupId,
      this.active,
      this.expirationDate,
      this.addresses,
      this.departments,
      this.preferredContactTypeIds,
      this.preferredContactTypeId,
      type,
      this.primaryAddress);
  }
}
