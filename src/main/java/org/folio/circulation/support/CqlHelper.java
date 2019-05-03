package org.folio.circulation.support;

import java.util.Collection;
import java.util.Objects;

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

    Result<CqlQuery> valueQuery = CqlQuery.exactMatchAny(indexName, valuesToSearchFor);

    if(Objects.isNull(prefixQuery)) {
      return valueQuery;
    }
    else {
      return valueQuery.map(prefixQuery::and);
    }
  }
}
