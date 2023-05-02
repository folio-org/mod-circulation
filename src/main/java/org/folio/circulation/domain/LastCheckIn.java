
package org.folio.circulation.domain;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getUUIDProperty;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import io.vertx.core.json.JsonObject;
import lombok.ToString;

@ToString
public class LastCheckIn {

  private final ZonedDateTime dateTime;
  private final UUID servicePointId;
  private final String staffMemberId;
  private ServicePoint servicePoint;

  public LastCheckIn(ZonedDateTime dateTime, UUID servicePointId, String staffMemberId) {
    this.dateTime = dateTime;
    this.servicePointId = servicePointId;
    this.staffMemberId = staffMemberId;
  }

  public JsonObject toJson() {
    JsonObject entries = new JsonObject();
    write(entries, "servicePointId", servicePointId);
    write(entries, "staffMemberId", staffMemberId);
    write(entries, "dateTime", dateTime);
    return entries;
  }

  void setServicePoint(ServicePoint servicePoint) {
    this.servicePoint = servicePoint;
  }

  UUID getServicePointId() {
    return servicePointId;
  }

  public ZonedDateTime getDateTime() {
    return dateTime;
  }

  public ServicePoint getServicePoint() {
    return servicePoint;
  }

  private static LastCheckIn fromJson(JsonObject lastCheckIn) {
    return new LastCheckIn(
      getDateTimeProperty(lastCheckIn, "dateTime"),
      getUUIDProperty(lastCheckIn, "servicePointId"),
      getProperty(lastCheckIn, "staffMemberId")
    );
  }

  public static LastCheckIn fromItemJson(JsonObject itemJson) {
    if (itemJson == null) {
      return null;
    }

    return Optional.ofNullable(itemJson.getJsonObject("lastCheckIn"))
      .map(LastCheckIn::fromJson)
      .orElse(null);
  }
}
