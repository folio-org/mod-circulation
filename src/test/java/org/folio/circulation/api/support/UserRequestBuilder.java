package org.folio.circulation.api.support;

import io.vertx.core.json.JsonObject;

import java.util.UUID;

public class UserRequestBuilder {

  private final UUID id;
  private final String username;
  private final String lastName;
  private final String firstName;
  private final String barcode;

  public UserRequestBuilder() {
    this("Jones", "Steven", "785493025613");
  }

  private UserRequestBuilder(
    String lastName,
    String firstName,
    String barcode) {

    this.id = UUID.randomUUID();

    this.lastName = lastName;
    this.firstName = firstName;
    this.barcode = barcode;

    this.username = lastName.concat(firstName.substring(0, 1).toLowerCase());
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

    request.put("personal", new JsonObject()
      .put("lastName", this.lastName)
      .put("firstName", this.firstName));

    return request;
  }

  public UserRequestBuilder withName(String lastName, String firstName) {
    return new UserRequestBuilder(lastName, firstName, this.barcode);
  }

  public UserRequestBuilder withBarcode(String barcode) {
    return new UserRequestBuilder(this.lastName, this.firstName, barcode);
  }

  public UserRequestBuilder withNoBarcode() {
    return new UserRequestBuilder(this.lastName, this.firstName, null);
  }
}
