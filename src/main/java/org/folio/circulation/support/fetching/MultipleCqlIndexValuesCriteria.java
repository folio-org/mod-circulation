package org.folio.circulation.support.fetching;

import static org.folio.circulation.support.http.client.CqlQuery.noQuery;

import java.util.Collection;

import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.client.CqlQuery;

public class MultipleCqlIndexValuesCriteria {
  final String indexName;
  final Collection<String> values;
  final Result<CqlQuery> andQuery;

  private MultipleCqlIndexValuesCriteria(String indexName,
      Collection<String> values, Result<CqlQuery> andQuery) {

    this.indexName = indexName;
    this.values = values;
    this.andQuery = andQuery;
  }

  public static MultipleCqlIndexValuesCriteria byId(Collection<String> values) {
    return byIndex("id", values);
  }

  public static MultipleCqlIndexValuesCriteria byIndex(String indexName,
      Collection<String> values) {

    return new MultipleCqlIndexValuesCriteria(indexName, values, noQuery());
  }

  public MultipleCqlIndexValuesCriteria withQuery(Result<CqlQuery> andQuery) {
    return new MultipleCqlIndexValuesCriteria(indexName, values, andQuery);
  }
}
