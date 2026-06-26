package api.support.utl;

import static api.support.APITestContext.getOkapiHeadersFromContext;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.folio.circulation.support.http.client.Response;

import api.support.http.OkapiHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public final class BlockOverridesUtils {
  public static final String OVERRIDE_RENEWAL_PERMISSION = "circulation.override-renewal-block.post";
  public static final String OVERRIDE_PATRON_BLOCK_PERMISSION = "circulation.override-patron-block.post";
  private static final String OVERRIDABLE_BLOCK = "overridableBlock";

  public static List<String> getMissingPermissions(Response response) {
    return response.getJson().getJsonArray("errors")
      .stream()
      .filter(JsonObject.class::isInstance)
      .map(JsonObject.class::cast)
      .filter(error -> Objects.nonNull(error.getJsonObject(OVERRIDABLE_BLOCK)))
      .map(error -> error.getJsonObject(OVERRIDABLE_BLOCK))
      .map(block -> block.getJsonArray("missingPermissions"))
      .flatMap(JsonArray::stream)
      .map(String.class::cast)
      .distinct()
      .collect(Collectors.toList());
  }

  public static OkapiHeaders buildOkapiHeadersWithPermissions(String... permission) {
    return getOkapiHeadersFromContext().withOkapiPermissions(
      new JsonArray(List.of(permission)).encode());
  }

  public static List<String> getOverridableBlockNames(Response response) {
    return response.getJson().getJsonArray("errors")
      .stream()
      .filter(JsonObject.class::isInstance)
      .map(JsonObject.class::cast)
      .filter(error -> Objects.nonNull(error.getJsonObject(OVERRIDABLE_BLOCK)))
      .map(block -> block.getJsonObject(OVERRIDABLE_BLOCK))
      .map(overridableBlock -> overridableBlock.getString("name"))
      .collect(Collectors.toList());
  }
}
