package api.support.builders;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.IndividualResource;

public class RenewByBarcodeRequestBuilder extends JsonBuilder implements Builder {
  private final String itemBarcode;
  private final String userBarcode;

  public RenewByBarcodeRequestBuilder() {
    this(null, null);
  }

  private RenewByBarcodeRequestBuilder(
    String itemBarcode,
    String userBarcode) {

    this.itemBarcode = itemBarcode;
    this.userBarcode = userBarcode;
  }

  @Override
  public JsonObject create() {
    final JsonObject request = new JsonObject();

    put(request, "itemBarcode", this.itemBarcode);
    put(request, "userBarcode", this.userBarcode);

    return request;
  }

  public RenewByBarcodeRequestBuilder forItem(IndividualResource item) {
    return new RenewByBarcodeRequestBuilder(
      getBarcode(item),
      this.userBarcode);
  }

  public RenewByBarcodeRequestBuilder forUser(IndividualResource loanee) {
    return new RenewByBarcodeRequestBuilder(
      this.itemBarcode,
      getBarcode(loanee));
  }

  private String getBarcode(IndividualResource record) {
    return record.getJson().getString("barcode");
  }
}
