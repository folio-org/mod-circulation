package org.folio.circulation.support.utils;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.map.CaseInsensitiveMap;

import io.vertx.core.json.JsonArray;

public class OkapiHeadersUtils {

  private OkapiHeadersUtils() {
    throw new UnsupportedOperationException("Utility class, do not instantiate");
  }

  public static List<String> getOkapiPermissions(Map<String, String> okapiHeaders) {
    String permissionsArrayString = new CaseInsensitiveMap<>(okapiHeaders)
      .getOrDefault("x-okapi-permissions", "[]");

    return new JsonArray(permissionsArrayString)
      .stream()
      .filter(String.class::isInstance)
      .map(String.class::cast)
      .collect(toList());
  }
}
