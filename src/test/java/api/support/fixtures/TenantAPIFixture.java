package api.support.fixtures;

import static api.support.APITestContext.circulationModuleUrl;

import org.folio.circulation.support.http.client.Response;
import org.folio.rest.tools.PomReader;

import api.support.RestAssuredClient;
import io.vertx.core.json.JsonObject;

public class TenantAPIFixture {
  private final RestAssuredClient restAssuredClient;

  public TenantAPIFixture(RestAssuredClient restAssuredClient) {
    this.restAssuredClient = restAssuredClient;
  }

  public Response postTenant() {
    String moduleName = PomReader.INSTANCE.getModuleName();
    String currentVersion = PomReader.INSTANCE.getVersion();

    return restAssuredClient.post(
      new JsonObject().put("id", String.format("%s-%s", moduleName, currentVersion)),
      circulationModuleUrl("/_/tenant"), "tenant-api-test-request");
  }
}
