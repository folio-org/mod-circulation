package api.support;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getObjectProperty;

import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;

import io.vertx.core.json.JsonObject;

public class CheckInByBarcodeResponse extends IndividualResource {
  public CheckInByBarcodeResponse(Response response) {
    super(response);
  }

  public JsonObject getLoan() {
    return getObjectProperty(getJson(), "loan");
  }

  public JsonObject getItem() {
    return getObjectProperty(getJson(), "item");
  }

  public JsonObject getStaffSlipContext() {
    return getObjectProperty(getJson(), "staffSlipContext");
  }

  public boolean getInHouseUse() {
    return getBooleanProperty(getJson(), "inHouseUse");
  }
}
