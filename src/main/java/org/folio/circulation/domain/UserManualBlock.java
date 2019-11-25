package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.joda.time.DateTime;

import java.util.UUID;

import static org.folio.circulation.support.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getUUIDProperty;

public class UserManualBlock {

  private final UUID id;
  private final String type;
  private final String desc;
  private final String staffInformation;
  private final String patronMessage;
  private final DateTime expirationDate;
  private final boolean borrowing;
  private final boolean renewals;
  private final boolean requests;
  private final String userId;

  private UserManualBlock(UUID id, String type,
                          String desc, String staffInformation,
                          String patronMessage, DateTime expirationDate,
                          boolean borrowing, boolean renewals,
                          boolean requests, String userId) {
    this.id = id;
    this.type = type;
    this.desc = desc;
    this.staffInformation = staffInformation;
    this.patronMessage = patronMessage;
    this.expirationDate = expirationDate;
    this.borrowing = borrowing;
    this.renewals = renewals;
    this.requests = requests;
    this.userId = userId;
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
      getUUIDProperty(representation, "id"),
      getProperty(representation, "type"),
      getProperty(representation, "desc"),
      getProperty(representation, "staffInformation"),
      getProperty(representation, "patronMessage"),
      getDateTimeProperty(representation, "expirationDate"),
      getBooleanProperty(representation, "borrowing"),
      getBooleanProperty(representation, "renewals"),
      getBooleanProperty(representation, "requests"),
      getProperty(representation, "userid")
    );
  }
}
