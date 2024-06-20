package org.folio.circulation.domain;

import static lombok.AccessLevel.PRIVATE;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getObjectProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import java.lang.invoke.MethodHandles;

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
    if (getProperty(representation, "name") == null ||
      getObjectProperty(representation, "value") == null) {

      log.info("from:: Circulation setting JSON is malformed: {}", representation);
      return null;
    }

    return new CirculationSetting(representation, getProperty(representation, "id"),
      getProperty(representation, "name"), getObjectProperty(representation, "value"));
  }
}
