package org.folio.circulation.support.fetching;

import static org.apache.commons.collections4.ListUtils.partition;
import static org.folio.circulation.support.utils.LogUtil.collectionAsString;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.results.Result;

import lombok.val;

public class FetchUtil {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private FetchUtil() {
  }

  public static List<Result<CqlQuery>> buildBatchQueriesByIndexName(
    MultipleCqlIndexValuesCriteria criteria, int maxValuesPerCqlSearchQuery) {

    log.debug("buildBatchQueriesByIndexName:: parameters criteria: {}, ." +
        "maxValuesPerCqlSearchQuery: {}", () -> collectionAsString(criteria.getValues()),
      () -> maxValuesPerCqlSearchQuery);

    val indexName = criteria.getIndexName();
    val indexOperator = criteria.getIndexOperator();
    val values = criteria.getValues();

    return partition(new ArrayList<>(values), maxValuesPerCqlSearchQuery)
      .stream()
      .map(partitionedIds -> indexOperator.apply(indexName, partitionedIds))
      .collect(Collectors.toList());
  }
}
