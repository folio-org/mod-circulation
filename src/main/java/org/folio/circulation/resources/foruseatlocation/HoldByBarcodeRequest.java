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
public class HoldByBarcodeRequest {
  private static final String ITEM_BARCODE = "itemBarcode";

  private final String itemBarcode;

  static Result<HoldByBarcodeRequest> buildRequestFrom(JsonObject json) {
    final String itemBarcode = getProperty(json, ITEM_BARCODE);

    if (isBlank(itemBarcode)) {
      return failedValidation("Request to put item on hold shelf must have an item barcode",
        ITEM_BARCODE, null);
    }

    return succeeded(new HoldByBarcodeRequest(itemBarcode));

  }
}
