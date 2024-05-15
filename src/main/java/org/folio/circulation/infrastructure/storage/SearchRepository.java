package org.folio.circulation.infrastructure.storage;

import lombok.AllArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.InstanceExtended;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.support.AsyncCoordinationUtil;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.results.Result;
import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import static org.folio.circulation.support.results.Result.emptyAsync;
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
    log.debug("getInstanceWithItems:: instanceId {}", instanceId);
    return searchClient.getManyWithQueryStringParameters(Map.of("expandAll",
        "true", "query", String.format("id==%s", instanceId)))
      .thenApply(flatMapResult(this::mapResponseToInstances))
      .thenApply(mapResult(MultipleRecords::firstOrNull))
      .thenCompose(r -> r.after(this::updateItemDetails));
  }

  private Result<MultipleRecords<InstanceExtended>> mapResponseToInstances(Response response) {
    return MultipleRecords.from(response, InstanceExtended::from, "instances");
  }

  private CompletableFuture<Result<InstanceExtended>> updateItemDetails(InstanceExtended searchInstance) {
    log.debug("updateItemDetails:: searchInstance {}", searchInstance);
    if (searchInstance == null) {
      return emptyAsync();
    }
    return AsyncCoordinationUtil.allOf(searchInstance.getItems(), this::fetchItemDetails)
      .thenApply(r -> r.map(searchInstance::changeItems));
  }

  private CompletableFuture<Result<Item>> fetchItemDetails(Item searchItem) {
    return itemRepository.fetchById(searchItem.getItemId())
      .thenComposeAsync(itemRepository::fetchItemRelatedRecords)
      .thenApply(r -> r.map(item -> item.changeTenantId(searchItem.getTenantId())));
  }
}
