package org.folio.circulation.infrastructure.storage;

import lombok.AllArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.InstanceExtended;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.results.Result;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.folio.circulation.support.results.ResultBinding.flatMapResult;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

@AllArgsConstructor
public class SearchRepository {

  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final ItemRepository itemRepository;
  private final CollectionResourceClient searchClient;


  public SearchRepository(Clients clients) {
    this(new ItemRepository(clients), clients.searchClient());
  }

  public CompletableFuture<Result<InstanceExtended>> getInstanceWithItems(String instanceId) {
    return searchClient.getManyWithQueryStringParameters(Map.of("expandAll",
        "true", "query", String.format("id==%s", instanceId)))
      .thenApply(flatMapResult(this::mapResponseToInstances))
      .thenApply(mapResult(MultipleRecords::firstOrNull))
      .thenCompose(r -> r.map(this::updateItemDetails)
        .orElse(CompletableFuture.completedFuture(null)));
  }

  private Result<MultipleRecords<InstanceExtended>> mapResponseToInstances(Response response) {
    return MultipleRecords.from(response, InstanceExtended::from, "instances");
  }

  private CompletableFuture<Result<InstanceExtended>> updateItemDetails(InstanceExtended searchInstance) {
    List<CompletableFuture<Void>> futures = new ArrayList<>();
    List<Item> updatedItems = new ArrayList<>();

    searchInstance.getItems().forEach(item -> {
      var tenantId = item.getTenantId();
      CompletableFuture<Void> updateFuture = itemRepository.fetchById(item.getItemId())
        .thenCompose(itemRepository::fetchItemRelatedRecords)
        .thenAccept(updatedItem -> {
          synchronized (updatedItems) {
            updatedItems.add(updatedItem.value().changeTenantId(tenantId));
          }
        });
      futures.add(updateFuture);
    });

    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
      .thenApply(v -> Result.of(() -> searchInstance.changeItems(updatedItems)));
  }
}
