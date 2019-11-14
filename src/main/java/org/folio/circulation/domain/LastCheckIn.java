
package org.folio.circulation.domain;

import static org.folio.circulation.support.JsonPropertyWriter.write;


import java.util.UUID;

import org.joda.time.DateTime;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import io.vertx.core.json.JsonObject;

/**
 * Information about when an item was last scanned in the Inventory app.
 *
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "dateTime", "servicePointId", "staffMemberId" })
public class LastCheckIn {

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
  private final UUID staffMemberId;

  public LastCheckIn(DateTime dateTime, UUID servicePointId, String staffMemberId) {
    this.dateTime = dateTime;
    this.servicePointId = servicePointId;
    this.staffMemberId = staffMemberId != null ? UUID.fromString(staffMemberId) : null;
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
