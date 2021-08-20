package api.support.http;

public class UserResource extends IndividualResource {
  public UserResource(IndividualResource resource) {
    super(resource.getResponse());
  }

  public String getBarcode() {
    return response.getJson().getString("barcode");
  }
}
