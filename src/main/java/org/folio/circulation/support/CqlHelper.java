package org.folio.circulation.support;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.lang.invoke.MethodHandles;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.stream.Collectors;

public class CqlHelper {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private CqlHelper() { }

  public static String multipleRecordsCqlQuery(Collection<String> recordIds) {
    if(recordIds.isEmpty()) {
      return null;
    }
    else {
      final Collection<String> filteredIds = recordIds.stream()
        .map(String::toString)
        .filter(StringUtils::isNotBlank)
        .distinct()
        .collect(Collectors.toList());

      if(filteredIds.isEmpty()) {
        return null;
      }

      String query = String.format("id==(%s)",
        filteredIds.stream().collect(Collectors.joining(" or ")));

      return encodeQuery(query).orElse(null);
    }
  }

  public static HttpResult<String> encodeQuery(String cqlQuery) {
    try {
      log.info("Encoding query {}", cqlQuery);

      return HttpResult.succeeded(URLEncoder.encode(cqlQuery,
        String.valueOf(StandardCharsets.UTF_8)));

    } catch (UnsupportedEncodingException e) {
      return HttpResult.failed(
        new ServerErrorFailure("Failed to encode CQL query"));
    }
  }
}
