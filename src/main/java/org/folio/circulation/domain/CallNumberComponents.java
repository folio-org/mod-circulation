package org.folio.circulation.domain;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import java.util.Optional;

import io.vertx.core.json.JsonObject;
import lombok.Value;

@Value
public class CallNumberComponents {
  String callNumber;
  String prefix;
  String suffix;

  public static CallNumberComponents unknown() {
    return new CallNumberComponents(null, null, null);
  }

  private CallNumberComponents(String callNumber, String prefix, String suffix) {
    this.callNumber = callNumber;
    this.prefix = prefix;
    this.suffix = suffix;
  }

  private static CallNumberComponents fromJson(JsonObject callNumberComponents) {
    return new CallNumberComponents(
      getProperty(callNumberComponents, "callNumber"),
      getProperty(callNumberComponents, "prefix"),
      getProperty(callNumberComponents, "suffix"));
  }

  /**
   * Constructs CallNumberComponents using Item json.
   *
   * @param itemJson - Inventory Item json.
   * @return new instance of CallNumberComponents.
   */
  public static CallNumberComponents fromItemJson(JsonObject itemJson) {
    if (itemJson == null) {
      return unknown();
    }

    return Optional.ofNullable(itemJson.getJsonObject("effectiveCallNumberComponents"))
      .map(CallNumberComponents::fromJson)
      .orElse(unknown());
  }
}
