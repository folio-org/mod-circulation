package api.support.fixtures;

import api.support.RestAssuredClient;
import api.support.builders.PickupByBarcodeRequestBuilder;
import org.folio.circulation.support.http.client.Response;

import static api.support.APITestContext.getOkapiHeadersFromContext;
import static api.support.http.InterfaceUrls.pickupForUseAtLocationUrl;

public class ForUseAtLocationPickupFixture {
  private final RestAssuredClient restAssuredClient;

  public ForUseAtLocationPickupFixture() {
    restAssuredClient = new RestAssuredClient(getOkapiHeadersFromContext());
  }

  public Response pickupForUseAtLocation(PickupByBarcodeRequestBuilder builder) {
    return restAssuredClient.post(builder.create(), pickupForUseAtLocationUrl(), 200,
      "pickup-for-use-at-location-by-barcode-request");
  }
}
