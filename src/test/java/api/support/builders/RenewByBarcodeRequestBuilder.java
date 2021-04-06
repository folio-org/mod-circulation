package api.support.builders;

import org.folio.circulation.domain.override.BlockOverrides;

import io.vertx.core.json.JsonObject;
import api.support.http.IndividualResource;

public class RenewByBarcodeRequestBuilder extends JsonBuilder implements Builder {
  private final String itemBarcode;
  private final String userBarcode;
  private final BlockOverrides blockOverrides;

  public RenewByBarcodeRequestBuilder() {
    this(null, null, null);
  }

  private RenewByBarcodeRequestBuilder(String itemBarcode, String userBarcode,
    BlockOverrides blockOverrides) {

    this.itemBarcode = itemBarcode;
    this.userBarcode = userBarcode;
    this.blockOverrides = blockOverrides;
  }

  @Override
  public JsonObject create() {
    final JsonObject request = new JsonObject();

    put(request, "itemBarcode", this.itemBarcode);
    put(request, "userBarcode", this.userBarcode);
    if (blockOverrides != null) {
      JsonObject overrideBlocksJson = new JsonObject();
      if (blockOverrides.getPatronBlockOverride() != null
        && blockOverrides.getPatronBlockOverride().isRequested()) {

        put(overrideBlocksJson, "patronBlock", new JsonObject());
      }
      put(overrideBlocksJson, "comment", blockOverrides.getComment());
      put(request, "overrideBlocks", overrideBlocksJson);
    }

    return request;
  }

  public RenewByBarcodeRequestBuilder forItem(IndividualResource item) {
    return new RenewByBarcodeRequestBuilder(
      getBarcode(item),
      this.userBarcode,
      this.blockOverrides);
  }

  public RenewByBarcodeRequestBuilder forUser(IndividualResource loanee) {
    return new RenewByBarcodeRequestBuilder(
      this.itemBarcode,
      getBarcode(loanee),
      this.blockOverrides);
  }

  public RenewByBarcodeRequestBuilder withOverrideBlocks(BlockOverrides blockOverrides) {
    return new RenewByBarcodeRequestBuilder(
      this.itemBarcode,
      this.userBarcode,
      blockOverrides);
  }

  private String getBarcode(IndividualResource record) {
    return record.getJson().getString("barcode");
  }
}
