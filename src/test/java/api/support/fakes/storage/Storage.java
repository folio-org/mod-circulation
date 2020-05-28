package api.support.fakes.storage;

import static api.support.APITestContext.getTenantId;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import io.vertx.core.json.JsonObject;

public final class Storage {
  private static final Storage storage = new Storage();

  private final Map<String, Map<String, JsonObject>> resources = new HashMap<>();

  public static Storage getStorage() {
    return storage;
  }

  public Map<String, JsonObject> getTenantResources(String rootPath, String tenant) {
    return resources.computeIfAbsent(getKey(rootPath, tenant), k -> new HashMap<>());
  }

  public Map<String, JsonObject> getTenantResources(URL wholePath) {
    return getTenantResources(wholePath.getPath(), getTenantId());
  }

  public void removeAll() {
    resources.clear();
  }

  private String getKey(String rootPath, String tenant) {
    return rootPath + tenant;
  }
}
