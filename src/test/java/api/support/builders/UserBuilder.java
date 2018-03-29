package api.support.builders;

import io.vertx.core.json.JsonObject;
import api.APITestSuite;

import java.util.UUID;

public class UserBuilder implements Builder {
  private final UUID id;
  private final String username;
  private final String lastName;
  private final String firstName;
  private final String middleName;
  private final String barcode;
  private final UUID patronGroupId;

  public UserBuilder() {
    this("sjones", "Jones", "Steven", null, "785493025613",
      APITestSuite.regularGroupId());
  }

  private UserBuilder(
    String username,
    String lastName,
    String firstName,
    String middleName,
    String barcode,
    UUID patronGroupId) {

    this.id = UUID.randomUUID();
    this.username = username;

    this.lastName = lastName;
    this.middleName = middleName;
    this.firstName = firstName;

    this.barcode = barcode;
    this.patronGroupId = patronGroupId;
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

    JsonObject personalInformation = new JsonObject()
      .put("lastName", this.lastName)
      .put("firstName", this.firstName);

    if(this.middleName != null) {
      personalInformation.put("middleName", this.middleName);
    }

    request.put("personal", personalInformation);

    return request;
  }

  public UserBuilder withName(String lastName, String firstName) {
    return new UserBuilder(
      firstName.substring(0, 1).concat(lastName).toLowerCase(),
      lastName,
      firstName,
      this.middleName,
      this.barcode,
      this.patronGroupId);
  }

  public UserBuilder withName(String lastName, String firstName, String middleName) {
    return new UserBuilder(
      this.username,
      lastName,
      firstName,
      middleName,
      this.barcode,
      this.patronGroupId);
  }

  public UserBuilder withBarcode(String barcode) {
    return new UserBuilder(
      this.username,
      this.lastName,
      this.firstName,
      this.middleName,
      barcode,
      this.patronGroupId);
  }

  public UserBuilder withNoBarcode() {
    return new UserBuilder(
      this.username,
      this.lastName,
      this.firstName,
      this.middleName,
      null,
      this.patronGroupId);
  }

  public UserBuilder withUsername(String username) {
    return new UserBuilder(
      username,
      this.lastName,
      this.firstName,
      this.middleName,
      this.barcode,
      this.patronGroupId);
  }

  public UserBuilder withPatronGroupId(UUID patronGroupId) {
    return new UserBuilder(
      this.username,
      this.lastName,
      this.firstName,
      this.middleName,
      this.barcode,
      patronGroupId);
  }
}
