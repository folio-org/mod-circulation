
package org.folio.circulation.domain;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.folio.circulation.support.JsonPropertyWriter.write;

import java.lang.invoke.MethodHandles;
import java.util.UUID;

import io.vertx.core.json.JsonObject;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Information about when an item was last scanned in the Inventory app.
 *
 */
public class LastCheckIn {

  private static final Logger log = LoggerFactory.getLogger(
    MethodHandles.lookup().lookupClass());

  private final DateTime dateTime;
  private final UUID servicePointId;
  private final String staffMemberId;

  public LastCheckIn(DateTime dateTime, UUID servicePointId, String staffMemberId) {
    this.dateTime = dateTime;
    this.servicePointId = servicePointId;
    this.staffMemberId = resolveStaffMemberId(staffMemberId);
  }

  private String resolveStaffMemberId(String id){
    try {
      return UUID.fromString(id).toString();
    } catch (Exception e) {
      log.error(e.getMessage());
    }
    return EMPTY;
  }

  public JsonObject toJson() {
    JsonObject entries = new JsonObject();
    write(entries, "servicePointId", servicePointId);
    write(entries, "staffMemberId", staffMemberId);
    write(entries, "dateTime", dateTime);
    return entries;
  }
}
