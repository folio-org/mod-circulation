package org.folio.circulation.domain.representations;

import static org.folio.circulation.support.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getUUIDProperty;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;

import java.util.Arrays;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.Result;
import org.joda.time.DateTime;

import io.vertx.core.json.JsonObject;

public class CheckInByBarcodeRequest {
  private static final String ITEM_BARCODE = "itemBarcode";
  private static final String CHECK_IN_DATE = "checkInDate";
  private static final String SERVICE_POINT_ID = "servicePointId";
  public static final String CLAIMED_RETURNED_RESOLVED_BY = "claimedReturnedResolvedBy";

  private final String itemBarcode;
  private final UUID servicePointId;
  private final DateTime checkInDate;
  private final ClaimedReturnedResolvedBy claimedReturnedResolvedBy;

  private CheckInByBarcodeRequest(
    String itemBarcode,
    UUID servicePointId,
    DateTime checkInDate,
    ClaimedReturnedResolvedBy claimedReturnedResolvedBy) {

    this.itemBarcode = itemBarcode;
    this.servicePointId = servicePointId;
    this.checkInDate = checkInDate;
    this.claimedReturnedResolvedBy = claimedReturnedResolvedBy;
  }

  public static Result<CheckInByBarcodeRequest> from(JsonObject json) {
    final String itemBarcode = getProperty(json, ITEM_BARCODE);

    if (StringUtils.isBlank(itemBarcode)) {
      return failedValidation("Checkin request must have an item barcode",
        ITEM_BARCODE, null);
    }

    final UUID servicePointId = getUUIDProperty(json, SERVICE_POINT_ID);

    if (servicePointId == null) {
      return failedValidation("Checkin request must have a service point id",
        SERVICE_POINT_ID, null);
    }

    final DateTime checkInDate = getDateTimeProperty(json, CHECK_IN_DATE);

    if(checkInDate == null) {
      return failedValidation("Checkin request must have an check in date",
        CHECK_IN_DATE, null);
    }

    final String claimedReturnedResolvedByString = json
      .getString(CLAIMED_RETURNED_RESOLVED_BY);
    final ClaimedReturnedResolvedBy claimedReturnedResolvedBy =
      ClaimedReturnedResolvedBy.from(claimedReturnedResolvedByString);

    if (claimedReturnedResolvedByString != null
      && claimedReturnedResolvedBy == null) {

      return failedValidation("Unrecognized value provided for property",
        CLAIMED_RETURNED_RESOLVED_BY, claimedReturnedResolvedByString);
    }

    return succeeded(new CheckInByBarcodeRequest(itemBarcode, servicePointId,
      checkInDate, claimedReturnedResolvedBy));
  }

  public String getItemBarcode() {
    return itemBarcode;
  }

  public UUID getServicePointId() {
    return servicePointId;
  }

  public DateTime getCheckInDate() {
    return checkInDate;
  }

  public ClaimedReturnedResolvedBy getClaimedReturnedResolvedBy() {
    return claimedReturnedResolvedBy;
  }

  public enum ClaimedReturnedResolvedBy {
    FOUND_BY_LIBRARY("Found by library"),
    RETURNED_BY_PATRON("Returned by patron");

    private final String value;

    ClaimedReturnedResolvedBy(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }

    /**
     * Returns instance with the specified value.
     *
     * @param value - Value to return enum for.
     * @return Returns matched instance or null if no such instance present.
     */
    public static ClaimedReturnedResolvedBy from(String value) {
      return Arrays.stream(values())
        .filter(claimedReturnedResolvedBy -> claimedReturnedResolvedBy
          .value.equals(value))
        .findFirst()
        .orElse(null);
    }
  }
}
