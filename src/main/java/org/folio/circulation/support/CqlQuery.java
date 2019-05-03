package org.folio.circulation.support;

import static java.lang.String.format;
import static java.lang.String.join;
import static java.lang.String.valueOf;
import static org.folio.circulation.support.Result.of;

import java.lang.invoke.MethodHandles;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CqlQuery {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final String query;

  public static CqlQuery exactMatch(String index, String value) {
    return new CqlQuery(String.format("%s==\"%s\"", index, value));
  }

  static CqlQuery exactMatchAny(String indexName, Collection<String> values) {
    return new CqlQuery(format("%s==(%s)", indexName, join(" or ", values)));
  }

  public CqlQuery(String query) {
    this.query = query;
  }

  CqlQuery and(CqlQuery other) {
    return new CqlQuery(format("%s and %s", asText(), other.asText()));
  }

  Result<String> encode() {
    log.info("Encoding query {}", query);

    return of(() -> URLEncoder.encode(query, valueOf(StandardCharsets.UTF_8)));
  }

  String asText() {
    return query;
  }
}
