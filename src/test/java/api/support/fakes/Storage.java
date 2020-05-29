package api.support.fakes;

import java.util.HashMap;
import java.util.Map;

import io.vertx.core.json.JsonObject;

final class Storage {
  private static final Storage storage = new Storage();

  private final Map<String, Map<String, JsonObject>> resources = new HashMap<>();

  static Storage getStorage() {
    return storage;
  }

  Map<String, JsonObject> getTenantResources(String rootPath, String tenant) {
    return resources.computeIfAbsent(getKey(rootPath, tenant), k -> new HashMap<>());
  }

  void removeAll() {
    resources.clear();
  }

  private String getKey(String rootPath, String tenant) {
    return rootPath + tenant;
  }
}
