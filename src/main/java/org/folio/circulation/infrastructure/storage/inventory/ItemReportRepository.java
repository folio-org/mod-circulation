package org.folio.circulation.infrastructure.storage.inventory;

import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.http.client.PageLimit.limit;
import static org.folio.circulation.support.http.client.Offset.offset;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.ItemsReportFetcher;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.GetManyRecordsClient;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.http.client.CqlQuery;

public class ItemReportRepository {
  private final GetManyRecordsClient itemsClient;

  private static final int PAGE_LIMIT = 1000;

  public ItemReportRepository(Clients clients) {
    itemsClient = clients.itemsStorage();
  }

  public CompletableFuture<Result<ItemsReportFetcher>> getAllItemsByField(String fieldName, String fieldValue) {
    CompletableFuture<Result<ItemsReportFetcher>> future = new CompletableFuture<>();
    ItemsReportFetcher itemsReportFetcher = new ItemsReportFetcher(0, new ArrayList<>());
    fetchNextPage(itemsReportFetcher, future, fieldName, fieldValue);
    return future;
  }

  private ItemsReportFetcher fillResultItemContext(ItemsReportFetcher itemsReportFetcher,
                                                   Result<MultipleRecords<Item>> itemRecords) {
    List<Result<MultipleRecords<Item>>> resultListOfItems = itemsReportFetcher.getResultListOfItems();
    resultListOfItems.add(itemRecords);
    int newPageNumber = itemsReportFetcher.getCurrPageNumber() + 1;
    return new ItemsReportFetcher(newPageNumber, resultListOfItems);
  }

  private void fetchNextPage(ItemsReportFetcher itemsReportFetcher,
                             CompletableFuture<Result<ItemsReportFetcher>> future,
                             String fieldName, String fieldValue) {
    getItemsByField(itemsReportFetcher, fieldName, fieldValue)
      .thenApply(itemRecords -> {
          ItemsReportFetcher reportFetcher = fillResultItemContext(itemsReportFetcher, itemRecords);
          int totalRecords = itemRecords.value().getTotalRecords();

          if (totalRecords > reportFetcher.getPageOffset(PAGE_LIMIT)) {
            fetchNextPage(reportFetcher, future, fieldName, fieldValue);
          } else {
            future.complete(Result.of(() -> reportFetcher));
          }
          return itemRecords;
        }
      );
  }

  private CompletableFuture<Result<MultipleRecords<Item>>> getItemsByField(
    ItemsReportFetcher itemsReportFetcher, String fieldName, String fieldValue) {

    final Result<CqlQuery> itemStatusQuery = exactMatch(fieldName, fieldValue);
    int pageOffset = itemsReportFetcher.getCurrPageNumber() * PAGE_LIMIT;

    return itemStatusQuery
      .after(query -> itemsClient.getMany(query, limit(PAGE_LIMIT),
        offset(pageOffset)))
      .thenApply(result -> result
        .next(response -> MultipleRecords.from(response, Item::from, "items")));
  }
}
