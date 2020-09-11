package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.joda.time.DateTime;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

public class UserManualBlock {
  private final String desc;
  private final DateTime expirationDate;
  private final boolean requests;

  private UserManualBlock(String desc, DateTime expirationDate,
                          boolean requests) {
    this.desc = desc;
    this.expirationDate = expirationDate;
    this.requests = requests;
  }

  public DateTime getExpirationDate() {
    return expirationDate;
  }

  public String getDesc() {
    return desc;
  }

  public boolean getRequests() {
    return requests;
  }

  public static UserManualBlock from(JsonObject representation) {
    return new UserManualBlock(
      getProperty(representation, "desc"),
      getDateTimeProperty(representation, "expirationDate"),
      getBooleanProperty(representation, "requests")
    );
  }
}
