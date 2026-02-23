package org.folio.circulation.domain.configuration;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getBooleanProperty;

@AllArgsConstructor
@Getter
@ToString(onlyExplicitlyIncluded = true)
public class PrintHoldRequestsConfiguration {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  @ToString.Include
  private final boolean printHoldRequestsEnabled;

  public static PrintHoldRequestsConfiguration from(JsonObject jsonObject) {
    log.debug("from:: parsing configuration");
    return new PrintHoldRequestsConfiguration(getBooleanProperty(jsonObject,
      "printHoldRequestsEnabled"));
  }
}
