package api.support;

import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;

public class CheckInByBarcodeResponse extends IndividualResource {
  public CheckInByBarcodeResponse(Response response) {
    super(response);
  }
}
