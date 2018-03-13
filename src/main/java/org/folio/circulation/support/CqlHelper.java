package org.folio.circulation.support;

import io.vertx.core.json.JsonObject;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
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
      String query = String.format("id==(%s)", recordIds.stream()
        .map(String::toString)
        .distinct()
        .collect(Collectors.joining(" or ")));

      try {
        return URLEncoder.encode(query, "UTF-8");

      } catch (UnsupportedEncodingException e) {
        log.error(String.format("Cannot encode query %s", query));
        return null;
      }
    }
  }

  public static String buildisValidUserProxyQuery(JsonObject objectToValidate){
    //we got the id of the proxy record and user id, look for a record that indicates this is indeed a
    //proxy for this user id, and make sure that the proxy is valid by indicating that we
    //only want a match is the status is active and the expDate is in the future
    String proxyId = objectToValidate.getString("proxyUserId");
    if(proxyId != null) {
      DateTime expDate = new DateTime(DateTimeZone.UTC);
      String validateProxyQuery ="id="+proxyId
          +" and meta.status=Active"
          +" and meta.expirationDate>"+expDate.toString().trim();
      try {
        return URLEncoder.encode(validateProxyQuery, String.valueOf(StandardCharsets.UTF_8));
      } catch (UnsupportedEncodingException e) {
        log.error("Failed to encode query for proxies");
        return null;
      }
    }
    return null;
  }
}
