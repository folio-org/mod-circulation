package org.folio.circulation.support;

import static org.folio.circulation.support.Result.failed;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

public class CqlHelper {
  private CqlHelper() { }

  /**
   *
   * Creates a CQL query for matching a property to one of multiple values
   * intended to return multiple records. Typically used when fetching related
   * records e.g. fetching all groups for users, or items for loans
   *
   * @param prefixQuery fragment of CQL to include at the beginning
   *                            e.g. status.name=="Open" AND
   * @param indexName Name of the index (property) to match values to
   * @param valuesToSearchFor Values to search for, query should match any
   *                          against the index
   * @return null if there are no values to search for, otherwise a CQL
   * query that includes a fragment if provided and a clause for matching any
   * of the values
   */
  public static Result<CqlQuery> multipleRecordsCqlQuery(
    CqlQuery prefixQuery,
    String indexName,
    Collection<String> valuesToSearchFor) {

    final Collection<String> filteredValues = filterNullValues(valuesToSearchFor);

    if(filteredValues.isEmpty()) {
      return failed(new ServerErrorFailure(
        "Cannot fetch multiple records when no IDs are provided"));
    }

    return Result.of(() ->
      multipleRecordsUnencodedCqlQuery(prefixQuery, indexName, filteredValues));
  }

  private static CqlQuery multipleRecordsUnencodedCqlQuery(
    CqlQuery prefixQuery,
    String indexName,
    Collection<String> filteredValues) {

    CqlQuery valueQuery = CqlQuery.exactMatchAny(indexName, filteredValues);

    if(Objects.isNull(prefixQuery)) {
      return valueQuery;
    }
    else {
      return prefixQuery.and(valueQuery);
    }
  }

  private static List<String> filterNullValues(Collection<String> values) {
    return values.stream()
      .filter(Objects::nonNull)
      .map(String::toString)
      .filter(StringUtils::isNotBlank)
      .distinct()
      .collect(Collectors.toList());
  }
}
