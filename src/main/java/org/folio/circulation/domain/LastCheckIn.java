
package org.folio.circulation.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.vertx.core.json.JsonObject;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.UUID;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.folio.circulation.support.JsonPropertyWriter.write;

/**
 * Information about when an item was last scanned in the Inventory app.
 *
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "dateTime", "servicePointId", "staffMemberId" })
public class LastCheckIn {

  private static final Logger log = LoggerFactory.getLogger(
    MethodHandles.lookup().lookupClass());

  /**
   * Date and time of the last check in of the item.
   *
   */
  @JsonProperty("dateTime")
  @JsonPropertyDescription("Date and time of the last check in of the item.")
  private final DateTime dateTime;
  /**
   * A universally unique identifier (UUID), this is a 128-bit number used to identify a record and is shown in hex with dashes, for
   * example 6312d172-f0cf-40f6-b27d-9fa8feaf332f; the UUID version must be from 1-5; see https://dev.folio.org/guides/uuids/
   *
   */
  @JsonProperty("servicePointId")
  @JsonPropertyDescription("A universally unique identifier (UUID), this is a 128-bit number used to identify a record and is shown in hex with dashes, for example 6312d172-f0cf-40f6-b27d-9fa8feaf332f; the UUID version must be from 1-5; see https://dev.folio.org/guides/uuids/")
  private final UUID servicePointId;
  /**
   * A universally unique identifier (UUID), this is a 128-bit number used to identify a record and is shown in hex with dashes, for
   * example 6312d172-f0cf-40f6-b27d-9fa8feaf332f; the UUID version must be from 1-5; see https://dev.folio.org/guides/uuids/
   *
   */
  @JsonProperty("staffMemberId")
  @JsonPropertyDescription("A universally unique identifier (UUID), this is a 128-bit number used to identify a record and is shown in hex with dashes, for example 6312d172-f0cf-40f6-b27d-9fa8feaf332f; the UUID version must be from 1-5; see https://dev.folio.org/guides/uuids/")
  private final String staffMemberId;

  public LastCheckIn(DateTime dateTime, UUID servicePointId, String staffMemberId) {
    this.dateTime = dateTime;
    this.servicePointId = servicePointId;
    this.staffMemberId = resolveStaffMemberId(staffMemberId);
  }

  private String resolveStaffMemberId(String id){
    try {
      return id != null ? UUID.fromString(id).toString() : EMPTY;
    } catch (IllegalArgumentException e) {
      log.error(e.getMessage());
    }
    return EMPTY;
  }

  public static LastCheckIn from(JsonObject representation) {
    return representation != null ? representation.mapTo(LastCheckIn.class) : null;
  }

  public JsonObject toJson() {
    JsonObject entries = new JsonObject();
    write(entries, "servicePointId", servicePointId);
    write(entries, "staffMemberId", staffMemberId);
    write(entries, "dateTime", dateTime);
    return entries;
  }

  /**
   * Date and time of the last check in of the item.
   *
   */
  @JsonProperty("dateTime")
  public DateTime getDateTime() {
    return dateTime;
  }

  @JsonProperty("servicePointId")
  public UUID getServicePointId() {
    return servicePointId;
  }
}
