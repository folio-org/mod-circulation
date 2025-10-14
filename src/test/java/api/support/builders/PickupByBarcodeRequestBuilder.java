package api.support.builders;

import io.vertx.core.json.JsonObject;

public class PickupByBarcodeRequestBuilder extends JsonBuilder implements Builder {
  private final String itemBarcode;
  private final String userBarcode;

  public PickupByBarcodeRequestBuilder(
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
}
