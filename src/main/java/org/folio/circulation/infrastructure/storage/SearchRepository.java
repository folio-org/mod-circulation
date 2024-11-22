package org.folio.circulation.infrastructure.storage;

import static org.folio.circulation.support.StringUtil.urlEncode;
import static org.folio.circulation.support.results.Result.emptyAsync;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.ResultBinding.flatMapResult;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

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
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.Result;

import io.vertx.core.http.HttpClient;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class SearchRepository {

  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private final WebContext webContext;
  private final HttpClient httpClient;
  private final CollectionResourceClient searchClient;

  public SearchRepository(WebContext webContext, HttpClient httpClient) {
    this.webContext = webContext;
    this.httpClient = httpClient;
    this.searchClient = Clients.create(webContext, httpClient).searchClient();
  }

  public CompletableFuture<Result<SearchInstance>> getInstanceWithItems(List<String> queryParams) {
    log.debug("getInstanceWithItems:: query {}", queryParams);
    if (queryParams.isEmpty()) {
      return CompletableFuture.completedFuture(failed(new BadRequestFailure(
        "query is empty")));
    }
    return searchClient.getManyWithQueryStringParameters(Map.of("expandAll",
        "true", "query", urlEncode(queryParams.get(0))))
      .thenApply(flatMapResult(this::mapResponseToInstances))
      .thenApply(mapResult(MultipleRecords::firstOrNull))
      .thenCompose(r -> r.after(this::updateItemDetails));
  }

  private Result<MultipleRecords<SearchInstance>> mapResponseToInstances(Response response) {
    return MultipleRecords.from(response, SearchInstance::from, "instances");
  }

  private CompletableFuture<Result<SearchInstance>> updateItemDetails(SearchInstance searchInstance) {
    log.debug("updateItemDetails:: searchInstance {}", () -> searchInstance);
    if (searchInstance == null || searchInstance.getId() == null) {
      log.info("updateItemDetails:: searchInstance is empty");
      return emptyAsync();
    }

    Map<String, List<Item>> itemsByTenant = searchInstance.getItems()
      .stream()
      .collect(Collectors.groupingBy(Item::getTenantId));

    log.info("updateItemDetails:: fetching item details from tenants: {}", itemsByTenant::keySet);

    return AsyncCoordinationUtil.allOf(itemsByTenant, this::fetchItemDetails)
      .thenApply(r -> r.map(lists -> lists.stream().flatMap(Collection::stream).toList()))
      .thenApply(r -> r.map(searchInstance::changeItems));
  }

  private CompletableFuture<Result<List<Item>>> fetchItemDetails(String tenantId, List<Item> items) {
    ItemRepository itemRepository = new ItemRepository(Clients.create(webContext, httpClient, tenantId));

    return AsyncCoordinationUtil.allOf(items, item -> fetchItemDetails(item, itemRepository));
  }

  private CompletableFuture<Result<Item>> fetchItemDetails(Item searchItem,
    ItemRepository itemRepository) {

    return itemRepository.fetchById(searchItem.getItemId())
      .thenApply(r -> r.map(item -> item.changeTenantId(searchItem.getTenantId())));
  }
}
