package org.folio.circulation.domain;

import static lombok.AccessLevel.PRIVATE;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getObjectProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@AllArgsConstructor(access = PRIVATE)
@ToString(onlyExplicitlyIncluded = true)
public class CirculationSetting {
  @ToString.Include
  @Getter
  private final JsonObject representation;

  @Getter
  private final String id;

  @Getter
  private final String name;

  @Getter
  private final JsonObject value;

  public static CirculationSetting from(JsonObject representation) {
    return new CirculationSetting(representation, getProperty(representation, "id"),
      getProperty(representation, "name"), getObjectProperty(representation, "value"));
  }
}
