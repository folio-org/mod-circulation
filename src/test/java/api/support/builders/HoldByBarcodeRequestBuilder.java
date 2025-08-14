package api.support.builders;

import io.vertx.core.json.JsonObject;

public class HoldByBarcodeRequestBuilder extends JsonBuilder implements Builder {
  private final String itemBarcode;

  public HoldByBarcodeRequestBuilder(String itemBarcode) {
    this.itemBarcode = itemBarcode;
  }

  @Override
  public JsonObject create() {
    final JsonObject request = new JsonObject();
    put(request, "itemBarcode", this.itemBarcode);
    return request;
  }
}

