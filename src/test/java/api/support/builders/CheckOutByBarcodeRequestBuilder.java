package api.support.builders;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.IndividualResource;

public class CheckOutByBarcodeRequestBuilder implements Builder {
  private final String itemBarcode;
  private final String userBarcode;

  public CheckOutByBarcodeRequestBuilder() {
    this(null, null);
  }

  private CheckOutByBarcodeRequestBuilder(
    String itemBarcode,
    String userBarcode) {

    this.itemBarcode = itemBarcode;
    this.userBarcode = userBarcode;
  }

  @Override
  public JsonObject create() {
    return new JsonObject()
      .put("itemBarcode", this.itemBarcode)
      .put("userBarcode", this.userBarcode);
  }

  public CheckOutByBarcodeRequestBuilder forItem(IndividualResource item) {
    return new CheckOutByBarcodeRequestBuilder(
      item.getJson().getString("barcode"),
      this.userBarcode);
  }

  public CheckOutByBarcodeRequestBuilder to(IndividualResource loanee) {
    return new CheckOutByBarcodeRequestBuilder(
      this.itemBarcode, loanee.getJson().getString("barcode"));
  }
}
