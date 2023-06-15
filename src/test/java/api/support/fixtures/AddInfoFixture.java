package api.support.fixtures;

import static api.support.APITestContext.getOkapiHeadersFromContext;
import static api.support.http.InterfaceUrls.addInfoURL;

import org.folio.circulation.support.http.client.Response;

import api.support.RestAssuredClient;
import api.support.builders.AddInfoRequestBuilder;



public class AddInfoFixture {
  private final RestAssuredClient restAssuredClient;

  public AddInfoFixture() {
    restAssuredClient = new RestAssuredClient(getOkapiHeadersFromContext());
  }

  public Response addInfo(AddInfoRequestBuilder request) {
    return restAssuredClient.post(request.create(),
      addInfoURL(request.getLoanId()),204,"add-info-request");
  }
}
