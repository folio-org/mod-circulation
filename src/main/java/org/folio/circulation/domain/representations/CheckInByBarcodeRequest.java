package org.folio.circulation.domain.representations;

import static org.folio.circulation.support.HttpResult.succeeded;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.ValidationErrorFailure.failedResult;
import static org.folio.circulation.support.ValidationErrorFailure.failure;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.FindByBarcodeQuery;
import org.folio.circulation.domain.User;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ValidationErrorFailure;

import io.vertx.core.json.JsonObject;

public class CheckInByBarcodeRequest implements FindByBarcodeQuery {
  private static final String USER_BARCODE = "userBarcode";
  private static final String ITEM_BARCODE = "itemBarcode";
  private static final String SERVICE_POINT_ID = "servicePointId";

  private final String itemBarcode;
  private final String userBarcode;
  private final String servicePointId;

  private CheckInByBarcodeRequest(String itemBarcode, String userBarcode, String servicePointId) {
    this.itemBarcode = itemBarcode;
    this.userBarcode = userBarcode;
    this.servicePointId = servicePointId;
  }

  public static HttpResult<CheckInByBarcodeRequest> from(JsonObject json) {
    final String itemBarcode = getProperty(json, ITEM_BARCODE);

    if (StringUtils.isBlank(itemBarcode)) {
      return failedResult("Checkin request must have an item barcode", ITEM_BARCODE, null);
    }

    final String userBarcode = getProperty(json, USER_BARCODE);

    if (StringUtils.isBlank(userBarcode)) {
      return failedResult("Checkin request must have a user barcode", USER_BARCODE, null);
    }

    final String servicePointId = getProperty(json, SERVICE_POINT_ID);

    if (StringUtils.isBlank(servicePointId)) {
      return failedResult("Checkin request must have a service point id", SERVICE_POINT_ID, null);
    }

    return succeeded(new CheckInByBarcodeRequest(itemBarcode, userBarcode, servicePointId));
  }

  @Override
  public String getItemBarcode() {
    return itemBarcode;
  }

  @Override
  public String getUserBarcode() {
    return userBarcode;
  }

  public String getServicePointId() {
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
}
