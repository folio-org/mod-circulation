package org.folio.circulation.domain.representations;

import static java.lang.String.format;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getUUIDProperty;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@Getter
@AllArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class CheckInByBarcodeRequest {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private static final String ITEM_BARCODE = "itemBarcode";
  private static final String CHECK_IN_DATE = "checkInDate";
  private static final String SERVICE_POINT_ID = "servicePointId";
  public static final String CLAIMED_RETURNED_RESOLUTION = "claimedReturnedResolution";

  @ToString.Include
  private final String itemBarcode;
  @ToString.Include
  private final UUID servicePointId;
  private final ZonedDateTime checkInDate;
  private final ClaimedReturnedResolution claimedReturnedResolution;

  public static Result<CheckInByBarcodeRequest> from(JsonObject json) {
    log.debug("from:: parameters json: {}", json);

    final String itemBarcode = getProperty(json, ITEM_BARCODE);

    if (StringUtils.isBlank(itemBarcode)) {
      log.warn("from:: itemBarcode is blank");
      return failedValidation("Checkin request must have an item barcode",
        ITEM_BARCODE, null);
    }

    final UUID servicePointId = getUUIDProperty(json, SERVICE_POINT_ID);

    if (servicePointId == null) {
      log.warn("from:: servicePointId is null");
      return failedValidation("Checkin request must have a service point id",
        SERVICE_POINT_ID, null);
    }

    final ZonedDateTime checkInDate = getDateTimeProperty(json, CHECK_IN_DATE);

    if (checkInDate == null) {
      log.warn("from:: checkInDate is null");
      return failedValidation("Checkin request must have an check in date",
        CHECK_IN_DATE, null);
    }

    final String claimedReturnedResolvedByString = json
      .getString(CLAIMED_RETURNED_RESOLUTION);
    final ClaimedReturnedResolution claimedReturnedResolution =
      ClaimedReturnedResolution.from(claimedReturnedResolvedByString);

    if (claimedReturnedResolvedByString != null && claimedReturnedResolution == null) {
      log.warn("from:: claimedReturnedResolvedByString or claimedReturnedResolution is null");
      return failedValidation("Unrecognized value provided for property",
        CLAIMED_RETURNED_RESOLUTION, claimedReturnedResolvedByString);
    }

    return succeeded(new CheckInByBarcodeRequest(itemBarcode, servicePointId,
      checkInDate, claimedReturnedResolution));
  }

  public enum ClaimedReturnedResolution {
    FOUND_BY_LIBRARY("Found by library"),
    RETURNED_BY_PATRON("Returned by patron");

    private final String value;

    ClaimedReturnedResolution(String value) {
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
    public static ClaimedReturnedResolution from(String value) {
      log.debug("from:: parameters value: {}", value);

      return Arrays.stream(values())
        .filter(claimedReturnedResolution -> claimedReturnedResolution
          .getValue().equals(value))
        .findFirst()
        .orElse(null);
    }
  }
}
