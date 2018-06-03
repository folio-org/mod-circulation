package org.folio.circulation.resources;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.FindByBarcodeQuery;
import org.folio.circulation.support.HttpResult;

import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

public class RenewByBarcodeRequest implements FindByBarcodeQuery {
  private final String itemBarcode;
  private final String userBarcode;

  private RenewByBarcodeRequest(String itemBarcode, String userBarcode) {
    this.itemBarcode = itemBarcode;
    this.userBarcode = userBarcode;
  }

  public static HttpResult<RenewByBarcodeRequest> from(JsonObject json) {
    return HttpResult.success(new RenewByBarcodeRequest(
      getProperty(json, "itemBarcode"),
      getProperty(json, "userBarcode")));
  }

  @Override
  public String getItemBarcode() {
    return itemBarcode;
  }

  @Override
  public String getUserBarcode() {
    return userBarcode;
  }
}
