package api.support;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

import io.vertx.core.json.JsonObject;

public class JsonCollectionAssistant {
  public static Optional<JsonObject> getRecordById(
    Collection<JsonObject> collection, UUID id) {

    return getRecordById(collection.stream(), id);
  }

  public static Optional<JsonObject> getRecordById(
    Stream<JsonObject> stream, UUID id) {

    return stream
      .filter(request -> StringUtils.equals(request.getString("id"), id.toString()))
      .findFirst();
  }
}
