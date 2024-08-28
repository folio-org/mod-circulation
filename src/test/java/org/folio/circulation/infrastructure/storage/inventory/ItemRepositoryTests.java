package org.folio.circulation.infrastructure.storage.inventory;

import static api.support.matchers.FailureMatcher.isErrorFailureContaining;
import static api.support.matchers.ResultMatchers.succeeded;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.vertx.core.json.JsonArray;
import org.folio.circulation.domain.Holdings;
import org.folio.circulation.domain.Instance;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.ItemDescription;
import org.folio.circulation.domain.LoanType;
import org.folio.circulation.domain.Location;
import org.folio.circulation.domain.MaterialType;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;
import lombok.SneakyThrows;

class ItemRepositoryTests {
  @Test
  void canUpdateAnItemThatHasBeenFetched() {
    final var itemsClient = mock(CollectionResourceClient.class);
    final var repository = createRepository(itemsClient, null);

    final var itemId = UUID.randomUUID().toString();

    final var itemJson = new JsonObject()
      .put("id", itemId)
      .put("holdingsRecordId", UUID.randomUUID())
      .put("effectiveLocationId", UUID.randomUUID());

    mockedClientGet(itemsClient, itemJson.encodePrettily());

    when(itemsClient.put(any(), any())).thenReturn(ofAsync(
      () -> new Response(204, itemJson.toString(), "application/json")));

    final var fetchedItem = get(repository.fetchById(itemId))
      .value();

    final var updateResult = get(repository.updateItem(fetchedItem));

    verify(itemsClient).put(eq(itemId), any());

    assertThat(updateResult, succeeded());
  }

  @Test
  void cannotUpdateAnItemThatHasNotBeenFetched() {
    final var repository = createRepository(null, null);

    final var notFetchedItem = dummyItem();

    final var updateResult = get(repository.updateItem(notFetchedItem));

    assertThat(updateResult, isErrorFailureContaining(
      "Cannot update item when original representation is not available in identity map"));
  }

  @Test
  void nullItemIsNotUpdated() {
    final var repository = createRepository(null, null);

    final var updateResult = get(repository.updateItem(null));

    assertThat(updateResult, succeeded());
    assertThat(updateResult.value(), is(nullValue()));
  }

  @Test
  void returnCirculationItemWhenNotFound() {
    final var itemsClient = mock(CollectionResourceClient.class);
    final var circulationItemClient = mock(CollectionResourceClient.class);
    final var barcode = "HZFRKBNXIA";
    final var repository = createRepository(itemsClient, circulationItemClient);

    final var circulationItemJson = new JsonObject();
    circulationItemJson.put("id", "673bc784-6536-4286-a528-b0de544cf037");
    circulationItemJson.put("dcbItem", true);
    circulationItemJson.put("barcode", barcode);
    circulationItemJson.put("lendingLibraryCode", "123456");
    circulationItemJson.put("holdingsRecordId", "d0b9f1c8-8f3d-4c7e-8aef-5b9b5a7f9b7e");

    final var emptyResult = new JsonObject()
      .put("items", new JsonArray()).toString();
    final var circulationItems = new JsonObject()
      .put("items", new JsonArray().add(circulationItemJson));

    when(itemsClient.getMany(any(), any())).thenReturn(ofAsync(
      () -> new Response(200, emptyResult, "application/json")));
    when(circulationItemClient.getMany(any(), any())).thenReturn(ofAsync(
      () -> new Response(200, circulationItems.toString(), "application/json")));
    assertThat(get(repository.fetchByBarcode(barcode)).value().getBarcode(), is(barcode));
  }

  @Test
  void canUpdateCirculationItemThatHasBeenFetched(){
    final var itemsClient = mock(CollectionResourceClient.class);
    final var circulationItemClient = mock(CollectionResourceClient.class);
    final var itemId = UUID.randomUUID().toString();
    final var repository = createRepository(itemsClient, circulationItemClient);

    final var circulationItemJson = new JsonObject()
      .put("id", itemId)
      .put("holdingsRecordId", UUID.randomUUID())
      .put("effectiveLocationId", UUID.randomUUID())
      .put("dcbItem", true);
    final var emptyResult = new JsonObject()
      .put("items", new JsonArray()).toString();

    mockedClientGet(itemsClient, circulationItemJson.encodePrettily());
    when(itemsClient.get(anyString())).thenReturn(ofAsync(
      () -> new Response(200, emptyResult, "application/json")));
    when(circulationItemClient.put(any(), any())).thenReturn(ofAsync(
      () -> new Response(204, circulationItemJson.toString(), "application/json")));

    final var fetchedItem = get(repository.fetchById(itemId)).value();
    final var updateResult = get(repository.updateItem(fetchedItem));

    assertThat(updateResult, succeeded());
  }

  private void mockedClientGet(CollectionResourceClient client, String body) {
    when(client.get(anyString())).thenReturn(ofAsync(
      () -> new Response(200, body, "application/json")));
  }

  private ItemRepository createRepository(CollectionResourceClient itemsClient, CollectionResourceClient circulationItemClient) {
    final var locationRepository = mock(LocationRepository.class);
    final var materialTypeRepository = mock(MaterialTypeRepository.class);
    final var instanceRepository = mock(InstanceRepository.class);
    final var holdingsRepository = mock(HoldingsRepository.class);
    final var loanTypeRepository = mock(LoanTypeRepository.class);


    when(locationRepository.getEffectiveLocation(any()))
      .thenReturn(ofAsync(Location::unknown));

    when(materialTypeRepository.getFor(any()))
      .thenReturn(ofAsync(MaterialType::unknown));

    when(instanceRepository.fetchById(anyString()))
      .thenReturn(ofAsync(Instance::unknown));

    when(holdingsRepository.fetchById(anyString()))
      .thenReturn(ofAsync(Holdings::unknown));

    return new ItemRepository(itemsClient, locationRepository,
      materialTypeRepository, instanceRepository,
      holdingsRepository, loanTypeRepository, circulationItemClient);
  }

  private Item dummyItem() {
    return new Item(null, null, null, null, null, null, null, null,
      null, false,
      Holdings.unknown(), Instance.unknown(), MaterialType.unknown(),
      LoanType.unknown(), ItemDescription.unknown());
  }

  @SneakyThrows
  private <T> Result<T> get(CompletableFuture<Result<T>> future) {
    return future.get(1, TimeUnit.SECONDS);
  }
}
