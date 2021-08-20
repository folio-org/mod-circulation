package api.support.builders;

import io.vertx.core.json.JsonObject;
import api.support.http.IndividualResource;

public class RenewByBarcodeRequestBuilder extends JsonBuilder implements Builder {
  private final String itemBarcode;
  private final String userBarcode;
  private final String servicePointId;
  private final JsonObject overrideBlocks;

  public RenewByBarcodeRequestBuilder() {
    this(null, null, null, null);
  }

  private RenewByBarcodeRequestBuilder(String itemBarcode, String userBarcode,
    String servicePointId, JsonObject overrideBlocks) {

    this.itemBarcode = itemBarcode;
    this.userBarcode = userBarcode;
    this.servicePointId = servicePointId;
    this.overrideBlocks = overrideBlocks;
  }

  @Override
  public JsonObject create() {
    final JsonObject request = new JsonObject();
    put(request, "itemBarcode", this.itemBarcode);
    put(request, "userBarcode", this.userBarcode);
    put(request, "servicePointId", this.servicePointId);
    put(request, "overrideBlocks", this.overrideBlocks);

    return request;
  }

  public RenewByBarcodeRequestBuilder forItem(IndividualResource item) {
    return new RenewByBarcodeRequestBuilder(
      getBarcode(item),
      this.userBarcode,
      this.servicePointId,
      this.overrideBlocks);
  }

  public RenewByBarcodeRequestBuilder forUser(IndividualResource loanee) {
    return new RenewByBarcodeRequestBuilder(
      this.itemBarcode,
      getBarcode(loanee),
      this.servicePointId,
      this.overrideBlocks);
  }

  public RenewByBarcodeRequestBuilder withServicePointId(String servicePointId) {
    return new RenewByBarcodeRequestBuilder(
      this.itemBarcode,
      this.userBarcode,
      servicePointId,
      this.overrideBlocks);
  }

  public RenewByBarcodeRequestBuilder withOverrideBlocks(JsonObject overrideBlocks) {
    return new RenewByBarcodeRequestBuilder(
      this.itemBarcode,
      this.userBarcode,
      this.servicePointId,
      overrideBlocks);
  }

  private String getBarcode(IndividualResource record) {
    return record.getJson().getString("barcode");
  }
}
