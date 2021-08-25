package org.folio.circulation.resources.renewal;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.Test;

import static api.support.matchers.FailureMatchers.errorResultFor;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

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

    assertThat(result, errorResultFor("itemBarcode",
      "Renewal request must have an item barcode"));
  }

  @Test
  void failWhenNoUserBarcode() {
    final Result<RenewByBarcodeRequest> result = RenewByBarcodeRequest.renewalRequestFrom(
      new JsonObject()
        .put("itemBarcode", "6404865493223234"));

    assertThat(result, errorResultFor("userBarcode",
      "Renewal request must have a user barcode"));
  }
}
