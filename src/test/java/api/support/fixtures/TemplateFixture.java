package api.support.fixtures;

import java.util.UUID;

import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;

public class TemplateFixture {

  private final ResourceClient templateClient;

  public TemplateFixture(ResourceClient templateClient) {
      this.templateClient = templateClient;
  }

  public void createDummyNoticeTemplate(UUID templateId) {
    templateClient.create(new JsonObject().put("id", templateId.toString()));
  }

  public void delete(UUID templateId) {
    templateClient.delete(templateId);
  }

  public void deleteAll() {
    templateClient.deleteAll();
  }
}
