package org.folio.circulation.resources.renewal;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.results.Result.succeeded;

import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RenewByBarcodeRequest {
  static final String ITEM_BARCODE = "itemBarcode";
  public static final String USER_BARCODE = "userBarcode";

  private final String itemBarcode;
  private final String userBarcode;

  static Result<RenewByBarcodeRequest> renewalRequestFrom(JsonObject json) {
    final String itemBarcode = getProperty(json, ITEM_BARCODE);

    if (isBlank(itemBarcode)) {
      return failedValidation("Renewal request must have an item barcode",
        ITEM_BARCODE, null);
    }

    final String userBarcode = getProperty(json, USER_BARCODE);

    if (isBlank(userBarcode)) {
      return failedValidation("Renewal request must have a user barcode",
        USER_BARCODE, null);
    }

    return succeeded(new RenewByBarcodeRequest(itemBarcode, userBarcode));
  }
}
