package org.folio.circulation.domain.configuration;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getBooleanProperty;

@AllArgsConstructor
@Getter
@ToString(onlyExplicitlyIncluded = true)
public class PrintHoldRequestsConfiguration {

  @ToString.Include
  private final boolean printHoldRequestsEnabled;

  public static PrintHoldRequestsConfiguration from(JsonObject jsonObject) {
    return new PrintHoldRequestsConfiguration(getBooleanProperty(jsonObject,
      "printHoldRequestsEnabled"));
  }
}
