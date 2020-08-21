package org.folio.circulation.support.fetching;

import static org.folio.circulation.support.http.client.CqlQuery.noQuery;

import java.util.Collection;
import java.util.function.BiFunction;

import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.http.client.CqlQuery;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

@Builder
@Getter
public class MultipleCqlIndexValuesCriteria {
  private final String indexName;
  @Singular
  private final Collection<String> values;
  @Builder.Default
  private final Result<CqlQuery> andQuery = noQuery();
  @Builder.Default
  private final BiFunction<String, Collection<String>, Result<CqlQuery>> indexOperator
    = CqlQuery::exactMatchAny;

  public static MultipleCqlIndexValuesCriteria byId(Collection<String> values) {
    return byIndex("id", values);
  }

  public static MultipleCqlIndexValuesCriteria byIndex(String indexName,
      Collection<String> values) {

    return builder().indexName(indexName).values(values).build();
  }

  public MultipleCqlIndexValuesCriteria withQuery(Result<CqlQuery> andQuery) {
    return new MultipleCqlIndexValuesCriteria(indexName, values, andQuery, indexOperator);
  }
}
