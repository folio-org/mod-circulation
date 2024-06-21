package org.folio.circulation.domain;

import static lombok.AccessLevel.PRIVATE;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getObjectProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import java.lang.invoke.MethodHandles;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@AllArgsConstructor(access = PRIVATE)
@ToString(onlyExplicitlyIncluded = true)
public class CirculationSetting {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public static final String ID_FIELD = "id";
  public static final String NAME_FIELD = "name";
  public static final String VALUE_FIELD = "value";
  public static final String METADATA_FIELD = "metadata";

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
    final var id = getProperty(representation, ID_FIELD);
    final var name = getProperty(representation, NAME_FIELD);
    final var value = getObjectProperty(representation, VALUE_FIELD);

    if (id == null || name == null || value == null || !containsOnlyKnownFields(representation)) {
      log.warn("from:: Circulation setting JSON is invalid: {}", representation);
      return null;
    }

    return new CirculationSetting(representation, id, name, value);
  }

  private static boolean containsOnlyKnownFields(JsonObject representation) {
    return Set.of(ID_FIELD, NAME_FIELD, VALUE_FIELD, METADATA_FIELD)
      .containsAll(representation.fieldNames());
  }
}
