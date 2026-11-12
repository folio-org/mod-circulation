package org.folio.circulation.resources.renewal;

import static api.support.matchers.ResultMatchers.hasValidationError;
import static api.support.matchers.ValidationErrorMatchers.hasCode;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasNullParameter;
import static org.folio.circulation.support.ErrorCode.RENEWAL_REQUEST_ITEM_BARCODE_REQUIRED;
import static org.folio.circulation.support.ErrorCode.RENEWAL_REQUEST_USER_BARCODE_REQUIRED;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.Test;

class RenewByBarcodeRequestTests {
  @Test
  void propertiesAreReadFromJson() {
    final Result<RenewByBarcodeRequest> request = RenewByBarcodeRequest.renewalRequestFrom(
      new JsonObject()
        .put("userBarcode", "534364324553")
        .put("itemBarcode", "659464843534564648"));

    assertThat(request.succeeded(), is(true));
    assertThat(request.value().getItemBarcode(), is("659464843534564648"));
    assertThat(request.value().getUserBarcode(), is("534364324553"));
  }

  @Test
  void failWhenNoItemBarcode() {
    final Result<RenewByBarcodeRequest> result = RenewByBarcodeRequest.renewalRequestFrom(
      new JsonObject()
        .put("userBarcode", "534364324553"));

    assertThat(result, hasValidationError(allOf(
      hasMessage("Renewal request must have an item barcode"),
      hasNullParameter("itemBarcode"),
      hasCode(RENEWAL_REQUEST_ITEM_BARCODE_REQUIRED))));
  }

  @Test
  void failWhenNoUserBarcode() {
    final Result<RenewByBarcodeRequest> result = RenewByBarcodeRequest.renewalRequestFrom(
      new JsonObject()
        .put("itemBarcode", "6404865493223234"));

    assertThat(result, hasValidationError(allOf(
      hasMessage("Renewal request must have a user barcode"),
      hasNullParameter("userBarcode"),
      hasCode(RENEWAL_REQUEST_USER_BARCODE_REQUIRED))));
  }
}
