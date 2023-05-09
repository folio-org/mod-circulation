package org.folio.circulation.domain;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import java.time.ZonedDateTime;

import io.vertx.core.json.JsonObject;
import lombok.ToString;

@ToString
public class UserManualBlock {
  private final String desc;
  private final ZonedDateTime expirationDate;
  private final boolean requests;
  private final boolean renewals;
  private final boolean borrowing;

  private UserManualBlock(String desc, ZonedDateTime expirationDate,
    boolean requests, boolean renewals, boolean borrowing) {

    this.desc = desc;
    this.expirationDate = expirationDate;
    this.requests = requests;
    this.renewals = renewals;
    this.borrowing = borrowing;
  }

  public ZonedDateTime getExpirationDate() {
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
