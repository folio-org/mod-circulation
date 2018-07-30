package api.support.builders;

import api.APITestSuite;
import io.vertx.core.json.JsonObject;
import org.joda.time.DateTime;

import java.util.UUID;

public class UserBuilder extends JsonBuilder implements Builder {
  private final UUID id;
  private final String username;
  private final String lastName;
  private final String firstName;
  private final String middleName;
  private final String barcode;
  private final UUID patronGroupId;
  private final Boolean active;
  private final DateTime expirationDate;

  public UserBuilder() {
    this("sjones", "Jones", "Steven", null, "785493025613",
      APITestSuite.regularGroupId(), true, null);
  }

  private UserBuilder(
    String username,
    String lastName,
    String firstName,
    String middleName,
    String barcode,
    UUID patronGroupId,
    Boolean active,
    DateTime expirationDate) {

    this.id = UUID.randomUUID();
    this.username = username;

    this.lastName = lastName;
    this.middleName = middleName;
    this.firstName = firstName;

    this.barcode = barcode;
    this.patronGroupId = patronGroupId;
    this.active = active;
    this.expirationDate = expirationDate;
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

    if(firstName != null || lastName != null) {
      JsonObject personalInformation = new JsonObject()
        .put("lastName", this.lastName)
        .put("firstName", this.firstName);

      if(this.middleName != null) {
        personalInformation.put("middleName", this.middleName);
      }

      request.put("personal", personalInformation);
    }

    return request;
  }

  public UserBuilder withName(String lastName, String firstName) {
    return new UserBuilder(
      firstName.substring(0, 1).concat(lastName).toLowerCase(),
      lastName,
      firstName,
      this.middleName,
      this.barcode,
      this.patronGroupId,
      this.active,
      this.expirationDate);
  }

  public UserBuilder withName(String lastName, String firstName, String middleName) {
    return new UserBuilder(
      this.username,
      lastName,
      firstName,
      middleName,
      this.barcode,
      this.patronGroupId,
      this.active,
      this.expirationDate);
  }

  public UserBuilder withNoPersonalDetails() {
    return new UserBuilder(
      this.username,
      null,
      null,
      null,
      this.barcode,
      this.patronGroupId,
      this.active,
      this.expirationDate);
  }

  public UserBuilder withBarcode(String barcode) {
    return new UserBuilder(
      this.username,
      this.lastName,
      this.firstName,
      this.middleName,
      barcode,
      this.patronGroupId,
      this.active,
      this.expirationDate);
  }

  public UserBuilder withNoBarcode() {
    return new UserBuilder(
      this.username,
      this.lastName,
      this.firstName,
      this.middleName,
      null,
      this.patronGroupId,
      this.active,
      this.expirationDate);
  }

  public UserBuilder withUsername(String username) {
    return new UserBuilder(
      username,
      this.lastName,
      this.firstName,
      this.middleName,
      this.barcode,
      this.patronGroupId,
      this.active,
      this.expirationDate);
  }

  public UserBuilder withPatronGroupId(UUID patronGroupId) {
    return new UserBuilder(
      this.username,
      this.lastName,
      this.firstName,
      this.middleName,
      this.barcode,
      patronGroupId,
      this.active,
      this.expirationDate);
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

  private UserBuilder withActive(Boolean active) {
    return new UserBuilder(
      this.username,
      this.lastName,
      this.firstName,
      this.middleName,
      this.barcode,
      this.patronGroupId,
      active,
      this.expirationDate);
  }

  public UserBuilder expires(DateTime newExpirationDate) {
    return new UserBuilder(
      this.username,
      this.lastName,
      this.firstName,
      this.middleName,
      this.barcode,
      this.patronGroupId,
      this.active,
      newExpirationDate);
  }

  public UserBuilder noExpiration() {
    return new UserBuilder(
      this.username,
      this.lastName,
      this.firstName,
      this.middleName,
      this.barcode,
      this.patronGroupId,
      this.active,
      null);
  }
}
