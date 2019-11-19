package org.folio.circulation.domain;

import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

import io.vertx.core.json.JsonObject;

public class CallNumberComponents {

  private final String callNumber;
  private final String callNumberPrefix;
  private final String callNumberSuffix;

  private CallNumberComponents(
    String callNumber, String callNumberPrefix, String callNumberSuffix) {

    this.callNumber = callNumber;
    this.callNumberPrefix = callNumberPrefix;
    this.callNumberSuffix = callNumberSuffix;
  }

  public String getCallNumber() {
    return callNumber;
  }

  public String getCallNumberPrefix() {
    return callNumberPrefix;
  }

  public String getCallNumberSuffix() {
    return callNumberSuffix;
  }

  static CallNumberComponents fromJson(JsonObject callNumberComponents) {
    return new CallNumberComponents(
      getProperty(callNumberComponents, "callNumber"),
      getProperty(callNumberComponents, "callNumberPrefix"),
      getProperty(callNumberComponents, "callNumberSuffix")
    );
  }
}
