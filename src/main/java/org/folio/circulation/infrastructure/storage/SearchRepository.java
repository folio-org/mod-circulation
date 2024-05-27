package org.folio.circulation.infrastructure.storage;

import static org.folio.circulation.support.StringUtil.urlEncode;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.ResultBinding.flatMapResult;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.SearchInstance;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.support.AsyncCoordinationUtil;
import org.folio.circulation.support.BadRequestFailure;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.results.Result;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class SearchRepository {

  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private final ItemRepository itemRepository;
  private final CollectionResourceClient searchClient;

  public SearchRepository(Clients clients) {
    this(new ItemRepository(clients), clients.searchClient());
  }

  public CompletableFuture<Result<SearchInstance>> getInstanceWithItems(String query) {
    log.debug("getInstanceWithItems:: query {}", query);
    return searchClient.getManyWithQueryStringParameters(Map.of("expandAll",
        "true", "query", urlEncode(query)))
      .thenApply(flatMapResult(this::mapResponseToInstances))
      .thenApply(mapResult(MultipleRecords::firstOrNull))
      .thenCompose(r -> r.after(this::updateItemDetails));
  }

  private Result<MultipleRecords<SearchInstance>> mapResponseToInstances(Response response) {
    return MultipleRecords.from(response, SearchInstance::from, "instances");
  }

  private CompletableFuture<Result<SearchInstance>> updateItemDetails(SearchInstance searchInstance) {
    log.debug("updateItemDetails:: searchInstance {}", () -> searchInstance);
    if (searchInstance == null) {
      return CompletableFuture.completedFuture(failed(new BadRequestFailure(
        "Search result is empty")));
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
