package org.folio.circulation.domain.representations;

import static org.folio.circulation.support.HttpResult.succeeded;
import static org.folio.circulation.support.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getUUIDProperty;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;

import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.HttpResult;
import org.joda.time.DateTime;

import io.vertx.core.json.JsonObject;

public class CheckInByBarcodeRequest {
  private static final String ITEM_BARCODE = "itemBarcode";
  private static final String CHECK_IN_DATE = "checkInDate";
  private static final String SERVICE_POINT_ID = "servicePointId";

  private final String itemBarcode;
  private final UUID servicePointId;
  private final DateTime checkInDate;

  private CheckInByBarcodeRequest(
    String itemBarcode,
    UUID servicePointId,
    DateTime checkInDate) {

    this.itemBarcode = itemBarcode;
    this.servicePointId = servicePointId;
    this.checkInDate = checkInDate;
  }

  public static HttpResult<CheckInByBarcodeRequest> from(JsonObject json) {
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

    return succeeded(new CheckInByBarcodeRequest(itemBarcode,
      servicePointId, checkInDate));
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
}
