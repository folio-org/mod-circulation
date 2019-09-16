package org.folio.circulation.domain;

import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

import io.vertx.core.json.JsonObject;


public class AddressType {
  private final String addressType;
  /**
   * Address Type description
   */
  private final String desc;
  private final String id;


  public AddressType(JsonObject representation) {
    this.addressType = getProperty(representation, "addressType");
    this.desc = getProperty(representation, "desc");
    this.id = getProperty(representation, "id");
  }

  public AddressType(String addressType, String desc, String id) {
    this.addressType = addressType;
    this.desc = desc;
    this.id = id;
  }

  public String getAddressType() {
    return addressType;
  }

  public String getDesc() {
    return desc;
  }

  public String getId() {
    return id;
  }
}
