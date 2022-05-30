package api.support.fixtures;

import static api.support.APITestContext.circulationModuleUrl;

import org.folio.circulation.support.http.client.Response;
import org.folio.util.pubsub.PubSubClientUtils;
import api.support.RestAssuredClient;
import io.vertx.core.json.JsonObject;

public class TenantActivationFixture {
  private final RestAssuredClient restAssuredClient;

  public TenantActivationFixture(RestAssuredClient restAssuredClient) {
    this.restAssuredClient = restAssuredClient;
  }

  public Response postTenant() {
    return restAssuredClient.post(
      new JsonObject().put("id", PubSubClientUtils.getModuleId()),
      circulationModuleUrl("/_/tenant"), "tenant-api-post-test-request");
  }

  public Response deleteTenant() {
    return restAssuredClient.delete(circulationModuleUrl("/_/tenant"),
      "tenant-api-delete-test-request");
  }
}
