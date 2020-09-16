package api.support.http;

import org.folio.circulation.support.http.client.Response;

public class UserResource extends IndividualResource {
  public UserResource(Response response) {
    super(response);
  }

  public String getBarcode() {
    return response.getJson().getString("barcode");
  }
}
