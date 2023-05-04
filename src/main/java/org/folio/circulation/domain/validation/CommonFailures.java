package org.folio.circulation.domain.validation;

import static java.lang.String.format;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.lang.invoke.MethodHandles;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.ServerErrorFailure;

public class CommonFailures {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private CommonFailures() { }

  public static Supplier<HttpFailure> moreThanOneOpenLoanFailure(String itemBarcode) {
    log.debug("moreThanOneOpenLoanFailure:: parameters itemBarcode: {}", itemBarcode);
    return () -> new ServerErrorFailure(
      format("More than one open loan for item %s", itemBarcode));
  }

  public static Supplier<HttpFailure> noItemFoundForBarcodeFailure(String itemBarcode) {
    log.debug("noItemFoundForBarcodeFailure:: parameters itemBarcode: {}", itemBarcode);
    return () -> singleValidationError(
      format("No item with barcode %s exists", itemBarcode),
      "itemBarcode", itemBarcode);
  }

  public static Supplier<HttpFailure> noItemFoundForIdFailure(String itemId) {
    log.debug("noItemFoundForIdFailure:: parameters itemId: {}", itemId);
    return () -> singleValidationError(format("No item with ID %s exists", itemId),
      "itemId", itemId);
  }
}
