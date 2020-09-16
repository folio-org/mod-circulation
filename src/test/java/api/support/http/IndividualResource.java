package api.support.http;

import java.util.UUID;

import org.folio.circulation.support.http.client.Response;

import io.vertx.core.json.JsonObject;

public class IndividualResource {
  protected final Response response;

  public IndividualResource(Response response) {
    this.response = response;
  }

  public UUID getId() {
    return UUID.fromString(response.getJson().getString("id"));
  }

  public JsonObject getJson() {
    return response.getJson().copy();
  }

  public JsonObject copyJson() {
    return response.getJson().copy();
  }

  public String getLocation() {
    return response.getHeader("location");
  }

  public Response getResponse() {
    return response;
  }

//  TODO: Possibly move this to own class
  public String getBarcode() {
    return response.getJson().getString("barcode");
  }
}
