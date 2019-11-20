
package org.folio.circulation.domain;

import static org.folio.circulation.support.JsonPropertyWriter.write;

import java.util.UUID;

import io.vertx.core.json.JsonObject;
import org.joda.time.DateTime;

public class LastCheckIn {

  private final DateTime dateTime;
  private final UUID servicePointId;
  private final String staffMemberId;

  public LastCheckIn(DateTime dateTime, UUID servicePointId, String staffMemberId) {
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
}
