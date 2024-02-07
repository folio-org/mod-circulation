package org.folio.circulation.support.fetching;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.Function;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.FindWithCqlQuery;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.Test;

public class CqlResultCombinerTest {

  @Test
  public void testFindByBatchQueries() {
    Function<Result<MultipleRecords<Item>>, Result<MultipleRecords<Item>>> combiner = mock(Function.class);
    FindWithCqlQuery<Item> cqlFinder = mock(FindWithCqlQuery.class);
    CqlResultCombiner<Item, Item> cqlResultCombiner = new CqlResultCombiner<>(cqlFinder, combiner);
    when(cqlFinder.findByQuery(any(), any())).thenReturn(completedFuture(succeeded(null)));
    cqlResultCombiner.findByBatchQueries(List.of(succeeded(null)));
    verify(cqlFinder, times(1)).findByQuery(any(), any());
    verify(combiner, times(1)).apply(any());
  }
}
