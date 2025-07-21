package org.folio.circulation.resources.foruseatlocation;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.folio.circulation.support.results.Result;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.results.Result.succeeded;

@Getter
@AllArgsConstructor
public class PickupByBarcodeRequest {
  private static final String ITEM_BARCODE = "itemBarcode";
  private static final String USER_BARCODE = "userBarcode";

  private final String itemBarcode;
  private final String userBarcode;

  static Result<PickupByBarcodeRequest> buildRequestFrom(JsonObject json) {
    final String itemBarcode = getProperty(json, ITEM_BARCODE);

    if (isBlank(itemBarcode)) {
      return failedValidation("Request to pick up from hold shelf must have an item barcode",
        ITEM_BARCODE, null);
    }

    final String userBarcode = getProperty(json, USER_BARCODE);

    if (isBlank(userBarcode)) {
      return failedValidation("Request to pick up from hold shelf must have a user barcode",
        USER_BARCODE, null);
    }

    return succeeded(new PickupByBarcodeRequest(itemBarcode, userBarcode));
  }
}

