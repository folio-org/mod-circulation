package org.folio.circulation.domain;

import static org.folio.circulation.support.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

import io.vertx.core.json.JsonObject;
import org.joda.time.DateTime;

public class ManualBlock {
  private final JsonObject representation;

  public ManualBlock(JsonObject representation) {
    this.representation = representation;
  }

  public DateTime getExpirationDate() {
      return getDateTimeProperty(representation, "expirationDate");
  }

  public String getDesc() {
    return getProperty(representation, "desc");
  }

  public boolean getRequests() {
    return getBooleanProperty(representation, "requests");
  }

  public static ManualBlock from(JsonObject representation) {
    return new ManualBlock(representation);
  }

  public JsonObject asJson() {
    return representation.copy();
  }
}
