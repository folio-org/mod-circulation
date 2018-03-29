package api.support;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public class JsonCollectionAssistant {
  public static Optional<JsonObject> getRecordById(
    Collection<JsonObject> collection,
    UUID id) {

    return collection.stream()
      .filter(request -> StringUtils.equals(request.getString("id"), id.toString()))
      .findFirst();
  }
}
