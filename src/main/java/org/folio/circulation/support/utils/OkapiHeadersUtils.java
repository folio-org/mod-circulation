package org.folio.circulation.support.utils;

import static org.folio.circulation.support.http.OkapiHeader.OKAPI_PERMISSIONS;

import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.map.CaseInsensitiveMap;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;

public class OkapiHeadersUtils {

  private OkapiHeadersUtils() {
    throw new UnsupportedOperationException("Utility class, do not instantiate");
  }

  public static List<String> getOkapiPermissions(Map<String, String> okapiHeaders) {
    String permissionsArrayString = new CaseInsensitiveMap<>(okapiHeaders)
      .getOrDefault(OKAPI_PERMISSIONS, "[]");

    return Splitter.on(',')
      .trimResults(CharMatcher.anyOf("[] "))
      .omitEmptyStrings()
      .splitToList(permissionsArrayString);
  }
}
