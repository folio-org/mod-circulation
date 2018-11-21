package org.folio.circulation.domain.representations;

import static org.folio.circulation.support.HttpResult.succeeded;
import static org.folio.circulation.support.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getUUIDProperty;
import static org.folio.circulation.support.ValidationErrorFailure.failedResult;
import static org.folio.circulation.support.ValidationErrorFailure.failure;

import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.FindByBarcodeQuery;
import org.folio.circulation.domain.User;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ValidationErrorFailure;
import org.joda.time.DateTime;

import io.vertx.core.json.JsonObject;

public class CheckInByBarcodeRequest implements FindByBarcodeQuery {
  private static final String USER_BARCODE = "userBarcode";
  private static final String ITEM_BARCODE = "itemBarcode";
  private static final String CHECK_IN_DATE = "checkInDate";
  private static final String SERVICE_POINT_ID = "servicePointId";

  private final String itemBarcode;
  private final String userBarcode;
  private final UUID servicePointId;
  private final DateTime checkInDate;

  private CheckInByBarcodeRequest(
    String itemBarcode,
    String userBarcode,
    UUID servicePointId,
    DateTime checkInDate) {

    this.itemBarcode = itemBarcode;
    this.userBarcode = userBarcode;
    this.servicePointId = servicePointId;
    this.checkInDate = checkInDate;
  }

  public static HttpResult<CheckInByBarcodeRequest> from(JsonObject json) {
    final String itemBarcode = getProperty(json, ITEM_BARCODE);

    if (StringUtils.isBlank(itemBarcode)) {
      return failedResult("Checkin request must have an item barcode", ITEM_BARCODE, null);
    }

    final UUID servicePointId = getUUIDProperty(json, SERVICE_POINT_ID);

    if (servicePointId == null) {
      return failedResult("Checkin request must have a service point id", SERVICE_POINT_ID, null);
    }

    final DateTime checkInDate = getDateTimeProperty(json, CHECK_IN_DATE);

    return succeeded(new CheckInByBarcodeRequest(itemBarcode, null, servicePointId, checkInDate));
  }

  @Override
  public String getItemBarcode() {
    return itemBarcode;
  }

  @Override
  public String getUserBarcode() {
    return userBarcode;
  }

  public UUID getServicePointId() {
    return servicePointId;
  }

  @Override
  public ValidationErrorFailure userDoesNotMatchError() {
    return failure("Cannot checkin item checked out to different user",
      USER_BARCODE, getUserBarcode());
  }

  @Override
  public boolean userMatches(User user) {
    return StringUtils.equals(user.getBarcode(), getUserBarcode());
  }

  public DateTime getCheckInDate() {
    return checkInDate;
  }
}
