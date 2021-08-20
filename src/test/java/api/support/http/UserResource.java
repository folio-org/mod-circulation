package api.support.http;

class UserResource extends IndividualResource {
  public UserResource(IndividualResource resource) {
    super(resource.getResponse());
  }

  public String getBarcode() {
    return response.getJson().getString("barcode");
  }
}
