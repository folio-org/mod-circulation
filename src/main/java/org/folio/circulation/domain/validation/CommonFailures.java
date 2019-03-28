package org.folio.circulation.domain.validation;

import static java.lang.String.format;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.util.function.Supplier;

import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.ServerErrorFailure;

public class CommonFailures {
  private CommonFailures() { }

  public static Supplier<HttpFailure> moreThanOneOpenLoanFailure(String itemBarcode) {
    return () -> new ServerErrorFailure(
      format("More than one open loan for item %s", itemBarcode));
  }

  public static Supplier<HttpFailure> noItemFoundForBarcodeFailure(String itemBarcode) {
    return () -> singleValidationError(
      format("No item with barcode %s exists", itemBarcode),
      "itemBarcode", itemBarcode);
  }

  public static Supplier<HttpFailure> noItemFoundForIdFailure(String itemId) {
    return () -> singleValidationError(format("No item with ID %s exists", itemId),
      "itemId", itemId);
  }
}
