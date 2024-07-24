package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.ToString;
import lombok.With;
import lombok.extern.log4j.Log4j2;

import java.time.ZonedDateTime;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getIntegerProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;


@AllArgsConstructor
@Log4j2
public class PrintEventDetail {
  @ToString.Include
  private final JsonObject representation;
  @With
  private final User printeduser;

  public static PrintEventDetail from(JsonObject representation) {
    return new PrintEventDetail(representation, null);
  }

  public String getUserId() {
    return getProperty(representation, "requesterId");
  }

  public String getRequestId() {
    return getProperty(representation, "requestId");
  }

  public int getCount() {
    return getIntegerProperty(representation, "count", 0);
  }

  public ZonedDateTime getPrintEventDate() {
    return getDateTimeProperty(representation, "printEventDate");
  }

  public User getUser() {
    return printeduser;
  }

}
