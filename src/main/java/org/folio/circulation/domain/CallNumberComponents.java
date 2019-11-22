package org.folio.circulation.domain;

import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

import java.util.Optional;

import io.vertx.core.json.JsonObject;

public class CallNumberComponents {

  private final String callNumber;
  private final String prefix;
  private final String suffix;

  private CallNumberComponents(String callNumber, String prefix, String suffix) {

    this.callNumber = callNumber;
    this.prefix = prefix;
    this.suffix = suffix;
  }

  public String getCallNumber() {
    return callNumber;
  }

  public String getPrefix() {
    return prefix;
  }

  public String getSuffix() {
    return suffix;
  }

  private static CallNumberComponents fromJson(JsonObject callNumberComponents) {
    return new CallNumberComponents(
      getProperty(callNumberComponents, "callNumber"),
      getProperty(callNumberComponents, "prefix"),
      getProperty(callNumberComponents, "suffix")
    );
  }

  /**
   * Constructs CallNumberComponents using Item json.
   *
   * @param itemJson - Inventory Item json.
   * @return new instance of CallNumberComponents.
   */
  static CallNumberComponents fromItemJson(JsonObject itemJson) {
    if (itemJson == null) {
      return null;
    }

    return Optional.ofNullable(itemJson.getJsonObject("effectiveCallNumberComponents"))
      .map(CallNumberComponents::fromJson)
      .orElse(null);
  }
}
