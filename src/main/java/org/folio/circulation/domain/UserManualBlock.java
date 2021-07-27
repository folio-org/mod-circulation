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
  private final boolean renewals;
  private final boolean borrowing;

  private UserManualBlock(String desc, DateTime expirationDate,
    boolean requests, boolean renewals, boolean borrowing) {

    this.desc = desc;
    this.expirationDate = expirationDate;
    this.requests = requests;
    this.renewals = renewals;
    this.borrowing = borrowing;
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

  public boolean getRenewals() {
    return renewals;
  }

  public boolean getBorrowing() {
    return borrowing;
  }

  public static UserManualBlock from(JsonObject representation) {
    return new UserManualBlock(
      getProperty(representation, "desc"),
      getDateTimeProperty(representation, "expirationDate"),
      getBooleanProperty(representation, "requests"),
      getBooleanProperty(representation, "renewals"),
      getBooleanProperty(representation, "borrowing")
      );
  }
}
