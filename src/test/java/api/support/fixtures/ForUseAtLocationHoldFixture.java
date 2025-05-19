package api.support.fixtures;

import api.support.RestAssuredClient;
import api.support.builders.HoldByBarcodeRequestBuilder;
import org.folio.circulation.support.http.client.Response;

import static api.support.APITestContext.getOkapiHeadersFromContext;
import static api.support.http.InterfaceUrls.holdForUseAtLocationUrl;

public class ForUseAtLocationHoldFixture {
  private final RestAssuredClient restAssuredClient;

  public ForUseAtLocationHoldFixture() {
    restAssuredClient = new RestAssuredClient(getOkapiHeadersFromContext());

  }

  public Response holdForUseAtLocation(HoldByBarcodeRequestBuilder builder) {
    return restAssuredClient.post(builder.create(), holdForUseAtLocationUrl(), 200,
      "hold-for-use-at-location-by-barcode-request");
  }
}
