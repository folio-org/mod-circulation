package org.folio.circulation.resources.foruseatlocation;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.support.results.Result;

import java.lang.invoke.MethodHandles;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.results.Result.succeeded;

@Getter
@AllArgsConstructor
public class HoldByBarcodeRequest {
  private static final String ITEM_BARCODE = "itemBarcode";
  private final String itemBarcode;

  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  static Result<HoldByBarcodeRequest> buildRequestFrom(JsonObject json) {
    final String itemBarcode = getProperty(json, ITEM_BARCODE);

    if (isBlank(itemBarcode)) {
      String message = "Request to put item on hold shelf must have an item barcode";
      log.warn("Missing information:: {}", message);
      return failedValidation(message, ITEM_BARCODE, null);
    }

    return succeeded(new HoldByBarcodeRequest(itemBarcode));

  }
}
