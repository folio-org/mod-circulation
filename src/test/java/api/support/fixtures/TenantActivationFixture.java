package api.support.fixtures;

import static api.support.APITestContext.circulationModuleUrl;

import org.folio.circulation.support.http.client.Response;

import api.support.RestAssuredClient;
import io.vertx.core.json.JsonObject;

public class TenantActivationFixture {
  private static final String MODULE_ID = "mod-circulation";
  private final RestAssuredClient restAssuredClient;

  public TenantActivationFixture(RestAssuredClient restAssuredClient) {
    this.restAssuredClient = restAssuredClient;
  }

  public Response postTenant() {
    return restAssuredClient.post(
      new JsonObject().put("id", MODULE_ID),
      circulationModuleUrl("/_/tenant"), "tenant-api-post-test-request");
  }

  public Response deleteTenant() {
    return restAssuredClient.delete(circulationModuleUrl("/_/tenant"),
      "tenant-api-delete-test-request");
  }

  public Response deleteTenant(boolean purge) {
    return restAssuredClient.delete(new JsonObject().put("purge", purge),
      circulationModuleUrl("/_/tenant"), "tenant-api-delete-test-request");
  }
}
