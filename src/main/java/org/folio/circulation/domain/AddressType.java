package org.folio.circulation.domain;

import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

import io.vertx.core.json.JsonObject;


public class AddressType {
  private final String addressType;
  private final String description;
  private final String id;


  public AddressType(JsonObject representation) {
    this.addressType = getProperty(representation, "addressType");
    this.description = getProperty(representation, "desc");
    this.id = getProperty(representation, "id");
  }

  public AddressType(String addressType, String description, String id) {
    this.addressType = addressType;
    this.description = description;
    this.id = id;
  }

  public String getAddressType() {
    return addressType;
  }

  public String getDescription() {
    return description;
  }

  public String getId() {
    return id;
  }
}
