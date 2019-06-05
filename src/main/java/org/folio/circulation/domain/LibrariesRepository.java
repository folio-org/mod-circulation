package org.folio.circulation.domain;

import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.MultipleRecordFetcher;
import org.folio.circulation.support.Result;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.Result.succeeded;

public class LibrariesRepository {

  private final CollectionResourceClient librariesStorageClient;

  public LibrariesRepository(Clients clients) {
    librariesStorageClient = clients.librariesStorage();
  }

  public CompletableFuture<Result<MultipleRecords<Request>>> findLibrariesForRequests(
    MultipleRecords<Request> multipleRequests) {
    Collection<Request> requests = multipleRequests.getRecords();

    final List<String> librariesToFetch =
      multipleRequests.getRecords()
        .stream()
        .filter(Objects::nonNull)
        .map(Request::getItem)
        .map(Item::getLibraryId)
        .collect(Collectors.toList());

    if (librariesToFetch.isEmpty()) {
      return completedFuture(succeeded(multipleRequests));
    }

    final MultipleRecordFetcher<Library> fetcher = createRequestsFetcher();

    return fetcher.findByIds(librariesToFetch)
      .thenApply(multipleRequestsResult -> multipleRequestsResult.next(
        multiRequests -> {
          Map<String, Library> result = multiRequests.getRecords()
            .stream()
            .collect(Collectors.toMap(Library::getId, Function.identity()));
          for (Request request : requests) {
           request.getItem().getLocation().put("libraryName",
              result.get(request.getItem().getLibraryId()).getName());
          }
          return succeeded(new MultipleRecords<>(requests, multipleRequests.getTotalRecords()));
        }));
  }

  private MultipleRecordFetcher<Library> createRequestsFetcher() {
    return new MultipleRecordFetcher<>(librariesStorageClient, "loclibs", Library::from);
  }
}
