package org.folio.circulation.resources.foruseatlocation;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
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
@ToString
public class PickupByBarcodeRequest {
  private static final String ITEM_BARCODE = "itemBarcode";
  private static final String USER_BARCODE = "userBarcode";

  private final String itemBarcode;
  private final String userBarcode;

  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  static Result<PickupByBarcodeRequest> buildRequestFrom(JsonObject json) {
    final String itemBarcode = getProperty(json, ITEM_BARCODE);

    if (isBlank(itemBarcode)) {
      String message = "Request to pick up from hold shelf must have an item barcode";
      log.warn("Missing information:: {}, parameter: {}", message, json);
      return failedValidation(message, ITEM_BARCODE, null);
    }

    final String userBarcode = getProperty(json, USER_BARCODE);

    if (isBlank(userBarcode)) {
      String message = "Request to pick up from hold shelf must have a user barcode";
      log.warn("Missing information:: {}, parameter: {}", message, json);
      return failedValidation(message, USER_BARCODE, null);
    }

    return succeeded(new PickupByBarcodeRequest(itemBarcode, userBarcode));
  }
}

