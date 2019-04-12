package org.folio.circulation.resources;

import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.Result;

import io.vertx.core.json.JsonObject;

public class RenewByBarcodeRequest {
  static final String ITEM_BARCODE = "itemBarcode";

  public static final String USER_BARCODE = "userBarcode";

  private final String itemBarcode;
  private final String userBarcode;

  RenewByBarcodeRequest(String itemBarcode, String userBarcode) {
    this.itemBarcode = itemBarcode;
    this.userBarcode = userBarcode;
  }

  static Result<RenewByBarcodeRequest> renewalRequestFrom(JsonObject json) {
    final String itemBarcode = getProperty(json, ITEM_BARCODE);

    if(StringUtils.isBlank(itemBarcode)) {
      return failedValidation("Renewal request must have an item barcode",
        ITEM_BARCODE, null);
    }

    final String userBarcode = getProperty(json, USER_BARCODE);

    if(StringUtils.isBlank(userBarcode)) {
      return failedValidation("Renewal request must have a user barcode",
        USER_BARCODE, null);
    }

    return succeeded(new RenewByBarcodeRequest(itemBarcode, userBarcode));
  }

  public String getItemBarcode() {
    return itemBarcode;
  }

  public String getUserBarcode() {
    return userBarcode;
  }
}
