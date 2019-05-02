package org.folio.circulation.support;

import static java.lang.String.valueOf;
import static org.folio.circulation.support.Result.of;

import java.lang.invoke.MethodHandles;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CqlQuery {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final String query;

  public CqlQuery(String query) {
    this.query = query;
  }

  Result<String> encode() {
    log.info("Encoding query {}", query);

    return of(() -> URLEncoder.encode(query, valueOf(StandardCharsets.UTF_8)));
  }
}
