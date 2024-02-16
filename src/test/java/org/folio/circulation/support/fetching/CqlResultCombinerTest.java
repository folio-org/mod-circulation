package org.folio.circulation.support.fetching;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.MultipleRecords.CombinationMatchers.matchRecordsById;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.mapResult;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.IntStream;

import org.folio.circulation.domain.Holdings;
import org.folio.circulation.domain.Instance;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.storage.mappers.InstanceMapper;
import org.folio.circulation.support.FindWithCqlQuery;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import api.support.builders.InstanceBuilder;
import io.vertx.core.json.JsonObject;

class CqlResultCombinerTest {

  @Test
  void testFindByBatchQueries() {
    Function<Result<MultipleRecords<Item>>, Result<MultipleRecords<Item>>> combiner = mock(Function.class);
    FindWithCqlQuery<Item> cqlFinder = mock(FindWithCqlQuery.class);
    CqlResultCombiner<Item, Item> cqlResultCombiner = new CqlResultCombiner<>(cqlFinder, combiner);
    when(cqlFinder.findByQuery(any(), any())).thenReturn(completedFuture(succeeded(null)));
    cqlResultCombiner.findByBatchQueries(List.of(succeeded(null)));
    verify(cqlFinder, times(1)).findByQuery(any(), any());
    verify(combiner, times(1)).apply(any());
  }

  @Test
  void testFindByBatchInstancesAndCombineWithItems() throws ExecutionException, InterruptedException {
    MultipleRecords<Item> itemRecords = createItemRecords(50);
    Function<Result<MultipleRecords<Instance>>, Result<MultipleRecords<Item>>> combiner =
      mapResult(instances -> itemRecords.combineRecords(instances, matchRecordsById(
        Item::getInstanceId, Instance::getId), Item::withInstance, Instance.unknown()));
    FindWithCqlQuery<Instance> cqlFinder = mock(FindWithCqlQuery.class);
    CqlResultCombiner<Instance, Item> cqlResultCombiner = new CqlResultCombiner<>(cqlFinder, combiner);

    when(cqlFinder.findByQuery(any(), any())).thenReturn(ofAsync(createInstanceRecords(itemRecords)));
    MultipleRecords<Item> combinedItems = cqlResultCombiner.findByBatchQueries(
      List.of(succeeded(null))).get().value();

    combinedItems.getRecords().forEach(item -> Assertions.assertNotNull(item.getInstance()));
    verify(cqlFinder, times(1)).findByQuery(any(), any());
  }

  private MultipleRecords<Item> createItemRecords(int number) {
    return new MultipleRecords<>(IntStream.rangeClosed(1, number)
      .mapToObj(i -> new JsonObject()
        .put("id", UUID.randomUUID()))
      .map(Item::from)
      .map(item -> item.withHoldings(new Holdings(UUID.randomUUID().toString(),
          UUID.randomUUID().toString(), "", UUID.randomUUID().toString())))
      .toList(), number);
  }

  private MultipleRecords<Instance> createInstanceRecords(MultipleRecords<Item> items) {
    return new MultipleRecords<>(items.getRecords().stream()
      .map(Item::getInstanceId)
      .map(instanceId -> new InstanceBuilder("Test title", UUID.randomUUID())
        .withId(UUID.fromString(instanceId)).create())
      .map(json -> new InstanceMapper().toDomain(json))
      .toList(), items.size());
  }
}
