package org.folio.circulation.support.http.client;

import static java.lang.String.format;
import static java.lang.String.join;
import static java.lang.String.valueOf;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static org.folio.circulation.support.CqlSortBy.none;
import static org.folio.circulation.support.Result.of;
import static org.folio.circulation.support.results.CommonFailures.failedDueToServerError;

import java.lang.invoke.MethodHandles;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.CqlSortBy;
import org.folio.circulation.support.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CqlQuery implements QueryParameter {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final String query;
  private final CqlSortBy sortBy;

  public static Result<CqlQuery> noQuery() {
    return Result.of(() -> new CqlQuery("", none()));
  }

  public static Result<CqlQuery> match(String index, String value) {
    return of(() -> new CqlQuery(String.format("%s=\"%s\"", index, value), none()));
  }

  public static Result<CqlQuery> exactMatch(String index, String value) {
    return Result.of(() -> new CqlQuery(format("%s==\"%s\"", index, value), none()));
  }

  public static Result<CqlQuery> exactMatchAny(String indexName, Collection<String> values) {
    final List<String> filteredValues = filterNullValues(values);

    if(filteredValues.isEmpty()) {
      return failedDueToServerError(
        format("Cannot generate CQL query using index %s matching no values", indexName));
    }

    return Result.of(() -> new CqlQuery(
      format("%s==(%s)", indexName, join(" or ", wrapValuesInQuotes(filteredValues))), none()));
  }

  /**
   * Uses greater than ('>'), as not equals operator ('<>') is not supported in CQL at present
   */
  public static Result<CqlQuery> greaterThan(String index, Object value) {
    return Result.of(() -> new CqlQuery(format("%s>\"%s\"", index, value), none()));
  }

  public static Result<CqlQuery> lessThan(String index, Object value) {
    return Result.of(() -> new CqlQuery(format("%s<\"%s\"", index, value), none()));
  }

  private CqlQuery(String query, CqlSortBy sortBy) {
    this.query = query;
    this.sortBy = sortBy;
  }

  public CqlQuery and(CqlQuery other) {
    if (StringUtils.isBlank(other.asText())) {
      return this;
    }

    return new CqlQuery(format("%s and %s", asText(), other.asText()), sortBy);
  }

  public CqlQuery sortBy(CqlSortBy sortBy) {
    return new CqlQuery(query, sortBy);
  }

  public Result<String> encode() {
    final String sortedQuery = asText();

    log.info("Encoding query {}", sortedQuery);

    return of(() -> URLEncoder.encode(sortedQuery, valueOf(UTF_8)));
  }

  String asText() {
    return sortBy.applyTo(query);
  }

  private static List<String> filterNullValues(Collection<String> values) {
    return values.stream()
      .filter(Objects::nonNull)
      .map(String::toString)
      .filter(StringUtils::isNotBlank)
      .distinct()
      .collect(toList());
  }

  private static List<String> wrapValuesInQuotes(List<String> values) {
    return values.stream()
      .map(value -> format("\"%s\"", value))
      .collect(toList());
  }

  @Override
  public void consume(QueryStringParameterConsumer consumer) {
    consumer.consume("query", asText());
  }

  @Override
  public String toString() {
    return String.format("A CQL query of \"%s\"", asText());
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;

    if (other == null || getClass() != other.getClass()) return false;

    CqlQuery otherCqlQuery = (CqlQuery) other;

    return StringUtils.equals(asText(), otherCqlQuery.asText());
  }

  @Override
  public int hashCode() {
    return Objects.hash(asText());
  }
}
