package org.folio.circulation.domain;

import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AddressType {
  private final String id;
  private final String name;
  private final String description;

  public static AddressType fromJson(JsonObject representation) {
    return new AddressType(getProperty(representation, "id"),
      getProperty(representation, "addressType"), getProperty(representation, "desc"));
  }
}
