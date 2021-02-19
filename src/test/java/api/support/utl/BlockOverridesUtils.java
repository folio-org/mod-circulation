package api.support.utl;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.folio.circulation.support.http.client.Response;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public final class BlockOverridesUtils {
  public static List<String> getMissingPermissions(Response response) {
    return response.getJson().getJsonArray("errors")
      .stream()
      .filter(JsonObject.class::isInstance)
      .map(JsonObject.class::cast)
      .filter(error -> Objects.nonNull(error.getJsonObject("overridableBlock")))
      .map(error -> error.getJsonObject("overridableBlock"))
      .map(block -> block.getJsonArray("missingPermissions"))
      .flatMap(JsonArray::stream)
      .map(String.class::cast)
      .distinct()
      .collect(Collectors.toList());
  }
}
