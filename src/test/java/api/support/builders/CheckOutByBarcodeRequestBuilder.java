package api.support.builders;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;

public class CheckOutByBarcodeRequestBuilder extends JsonBuilder implements Builder {
  private final String itemBarcode;
  private final String userBarcode;
  private final DateTime loanDate;

  public CheckOutByBarcodeRequestBuilder() {
    this(null, null, null);
  }

  private CheckOutByBarcodeRequestBuilder(
    String itemBarcode,
    String userBarcode, DateTime loanDate) {

    this.itemBarcode = itemBarcode;
    this.userBarcode = userBarcode;
    this.loanDate = loanDate;
  }

  @Override
  public JsonObject create() {
    final JsonObject request = new JsonObject();

    put(request, "itemBarcode", this.itemBarcode);
    put(request, "userBarcode", this.userBarcode);
    put(request, "loanDate", this.loanDate);

    return request;
  }

  public CheckOutByBarcodeRequestBuilder forItem(IndividualResource item) {
    return new CheckOutByBarcodeRequestBuilder(
      item.getJson().getString("barcode"),
      this.userBarcode,
      this.loanDate);
  }

  public CheckOutByBarcodeRequestBuilder to(IndividualResource loanee) {
    return new CheckOutByBarcodeRequestBuilder(
      this.itemBarcode, loanee.getJson().getString("barcode"),
      this.loanDate);
  }

  public CheckOutByBarcodeRequestBuilder at(DateTime loanDate) {
    return new CheckOutByBarcodeRequestBuilder(
      this.itemBarcode,
      this.userBarcode,
      loanDate);
  }
}
