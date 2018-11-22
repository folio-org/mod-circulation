package api.support;

import static org.folio.circulation.support.JsonPropertyFetcher.getObjectProperty;

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
}
