package org.folio.circulation.support;

import static java.lang.String.format;
import static java.lang.String.join;
import static java.lang.String.valueOf;
import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.of;

import java.lang.invoke.MethodHandles;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CqlQuery {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final String query;

  public static Result<CqlQuery> exactMatch(String index, String value) {
    return Result.of(() -> new CqlQuery(format("%s==\"%s\"", index, value)));
  }

  public static Result<CqlQuery> exactMatchAny(String indexName, Collection<String> values) {
    final List<String> filteredValues = filterNullValues(values);

    if(filteredValues.isEmpty()) {
      return failed(new ServerErrorFailure(
        format("Cannot generate CQL query using index %s matching no values", indexName)));
    }

    return Result.of(() -> new CqlQuery(
      format("%s==(%s)", indexName, join(" or ", filteredValues))));
  }

  private static List<String> filterNullValues(Collection<String> values) {
    return values.stream()
      .filter(Objects::nonNull)
      .map(String::toString)
      .filter(StringUtils::isNotBlank)
      .distinct()
      .collect(Collectors.toList());
  }

  public CqlQuery(String query) {
    this.query = query;
  }

  public CqlQuery and(CqlQuery other) {
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
