package org.folio.circulation.resources.renewal;

import static java.util.UUID.randomUUID;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.resources.renewal.BulkRenewalChunkedFetchSupport.ITEM_ID_CHUNK_SIZE;
import static org.folio.circulation.resources.renewal.BulkRenewalChunkedFetchSupport.partition;
import static org.folio.circulation.support.CqlSortBy.ascending;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatchAny;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.infrastructure.storage.inventory.HoldingsRepository;
import org.folio.circulation.infrastructure.storage.inventory.InstanceRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.inventory.LocationRepository;
import org.folio.circulation.infrastructure.storage.inventory.LoanTypeRepository;
import org.folio.circulation.infrastructure.storage.inventory.MaterialTypeRepository;
import org.folio.circulation.infrastructure.storage.inventory.ShadowLocationRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.Offset;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.http.client.Response;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class BulkRenewalChunkedFetchSupportTest {

  @Test
  void shouldPartitionValuesIntoConfiguredChunks() {
    assertEquals(List.of(List.of("1", "2"), List.of("3")),
      partition(List.of("1", "2", "3"), 2));
  }

  @Test
  void shouldPreserveInputOrderAcrossChunks() {
    assertEquals(List.of(List.of("c", "a"), List.of("b")),
      partition(List.of("c", "a", "b"), 2));
  }

  @Test
  void shouldIgnoreBlankIdentifiersWhenPartitioning() {
    List<String> values = new ArrayList<>();
    values.add("1");
    values.add("");
    values.add("   ");
    values.add(null);
    values.add("2");

    assertEquals(List.of(List.of("1", "2")),
      partition(values, 2));
  }

  @Test
  void shouldExposeItemIdChunkSizeForBulkRenewalFetches() {
    assertEquals(80, ITEM_ID_CHUNK_SIZE);
  }

  @Test
  void shouldCreateSortedOpenLoanPageQuery() {
    var clients = mock(Clients.class);
    var mockedCollectionResource = mock(CollectionResourceClient.class);
    when(clients.loansStorage()).thenReturn(mockedCollectionResource);
    when(clients.loansHistoryStorageClient()).thenReturn(mockedCollectionResource);

    var repository = new LoanRepository(clients,
      mock(ItemRepository.class), mock(UserRepository.class));

    var result = repository.createOpenLoanPageQuery();

    assertTrue(result.succeeded());
    assertEquals(exactMatch("status.name", "Open")
      .map(query -> query.sortBy(ascending("id"))).value(), result.value());
  }

  @Test
  void shouldFetchOpenLoanPageUsingSortedQueryAndOffset() {
    var clients = mock(Clients.class);
    var mockedCollectionResource = mock(CollectionResourceClient.class);

    when(clients.loansStorage()).thenReturn(mockedCollectionResource);
    when(clients.loansHistoryStorageClient()).thenReturn(mockedCollectionResource);
    when(mockedCollectionResource.getMany(any(CqlQuery.class), any(PageLimit.class), any(Offset.class)))
      .thenReturn(completedFuture(succeeded(emptyResponse("loans"))));

    var repository = new LoanRepository(clients,
      mock(ItemRepository.class), mock(UserRepository.class));
    var pageLimit = PageLimit.limit(5);
    var offset = Offset.offset(10);

    var result = repository.findOpenLoans(pageLimit, offset).join();

    assertTrue(result.succeeded());

    var queryCaptor = ArgumentCaptor.forClass(CqlQuery.class);
    verify(mockedCollectionResource).getMany(queryCaptor.capture(), eq(pageLimit), eq(offset));

    assertEquals(exactMatch("status.name", "Open")
      .map(query -> query.sortBy(ascending("id"))).value(), queryCaptor.getValue());
  }

  @Test
  void shouldFetchItemsUsingConfiguredChunkSize() {
    var itemsClient = mock(CollectionResourceClient.class);
    var circulationItemClient = mock(CollectionResourceClient.class);

    when(itemsClient.getMany(any(CqlQuery.class), any(PageLimit.class)))
      .thenReturn(completedFuture(succeeded(emptyResponse("items"))));
    when(circulationItemClient.getMany(any(CqlQuery.class), any(PageLimit.class)))
      .thenReturn(completedFuture(succeeded(emptyResponse("items"))));

    var repository = createItemRepository(itemsClient, circulationItemClient);
    var itemIds = generateIds(ITEM_ID_CHUNK_SIZE + 1);

    var result = repository.fetchFor(itemIds, ITEM_ID_CHUNK_SIZE).join();

    assertTrue(result.succeeded());

    var queryCaptor = ArgumentCaptor.forClass(CqlQuery.class);
    verify(itemsClient, times(2))
      .getMany(queryCaptor.capture(), any(PageLimit.class));

    var actualQueries = queryCaptor.getAllValues();

    assertEquals(exactMatchAny("id", itemIds.subList(0,
      ITEM_ID_CHUNK_SIZE)).value(), actualQueries.get(0));
    assertEquals(exactMatchAny("id", itemIds.subList(
      ITEM_ID_CHUNK_SIZE, itemIds.size())).value(),
      actualQueries.get(1));
  }

  @Test
  void shouldNormalizeItemIdsBeforeChunkedFetchCompletenessCheck() {
    var itemsClient = mock(CollectionResourceClient.class);
    var circulationItemClient = mock(CollectionResourceClient.class);
    var itemId = randomUUID().toString();

    when(itemsClient.getMany(any(CqlQuery.class), any(PageLimit.class)))
      .thenReturn(completedFuture(succeeded(responseWithItems(itemId))));

    var repository = createItemRepository(itemsClient, circulationItemClient);
    var itemIds = new ArrayList<String>();

    IntStream.range(0, ITEM_ID_CHUNK_SIZE + 1)
      .forEach(index -> itemIds.add(itemId));

    itemIds.add("");
    itemIds.add("   ");
    itemIds.add(null);

    var result = repository.fetchFor(itemIds, ITEM_ID_CHUNK_SIZE).join();

    assertTrue(result.succeeded());
    assertEquals(1, result.value().size());

    var queryCaptor = ArgumentCaptor.forClass(CqlQuery.class);
    verify(itemsClient, times(1)).getMany(queryCaptor.capture(), any(PageLimit.class));
    verify(circulationItemClient, times(0)).getMany(any(CqlQuery.class), any(PageLimit.class));

    assertEquals(exactMatchAny("id", List.of(itemId)).value(), queryCaptor.getValue());
  }

  @Test
  void shouldFetchUsersUsingConfiguredChunkSize() {
    var clients = mock(Clients.class);
    var usersStorageClient = mock(CollectionResourceClient.class);
    var patronGroupsStorageClient = mock(CollectionResourceClient.class);
    var addressTypesStorageClient = mock(CollectionResourceClient.class);

    when(clients.usersStorage()).thenReturn(usersStorageClient);
    when(clients.patronGroupsStorage()).thenReturn(patronGroupsStorageClient);
    when(clients.addressTypesStorage()).thenReturn(addressTypesStorageClient);
    when(usersStorageClient.getMany(any(CqlQuery.class), any(PageLimit.class)))
      .thenReturn(completedFuture(succeeded(emptyResponse("users"))));

    var repository = new UserRepository(clients);
    var userIds = generateIds(3);

    var result = repository.getUsersForUserIds(userIds, 2).join();

    assertTrue(result.succeeded());

    var queryCaptor = ArgumentCaptor.forClass(CqlQuery.class);
    verify(usersStorageClient, times(2)).getMany(queryCaptor.capture(), any(PageLimit.class));

    var actualQueries = queryCaptor.getAllValues();

    assertEquals(exactMatchAny("id", userIds.subList(0, 2)).value(), actualQueries.get(0));
    assertEquals(exactMatchAny("id", userIds.subList(2, userIds.size())).value(),
      actualQueries.get(1));
  }

  @Test
  void shouldFetchOpenRequestsUsingConfiguredChunkSize() {
    var clients = mock(Clients.class);
    var requestsStorageClient = mock(CollectionResourceClient.class);
    var requestsBatchStorageClient = mock(CollectionResourceClient.class);
    var cancellationReasonStorageClient = mock(CollectionResourceClient.class);

    when(clients.requestsStorage()).thenReturn(requestsStorageClient);
    when(clients.requestsBatchStorage()).thenReturn(requestsBatchStorageClient);
    when(clients.cancellationReasonStorage()).thenReturn(cancellationReasonStorageClient);
    when(requestsStorageClient.getMany(any(CqlQuery.class), any(PageLimit.class)))
      .thenReturn(completedFuture(succeeded(emptyResponse("requests"))));

    var repository = new RequestRepository(clients);
    var itemIds = generateIds(3);

    var result = repository.findOpenRequestsByItemIds(itemIds, 2).join();

    assertTrue(result.succeeded());

    var queryCaptor = ArgumentCaptor.forClass(CqlQuery.class);
    verify(requestsStorageClient, times(2)).getMany(queryCaptor.capture(), any(PageLimit.class));

    var openRequestsQuery = exactMatchAny("status", RequestStatus.openStates())
      .map(query -> query.sortBy(ascending("position")));

    var actualQueries = queryCaptor.getAllValues();

    assertEquals(exactMatchAny("itemId", itemIds.subList(0, 2)).value()
      .and(openRequestsQuery.value()), actualQueries.get(0));
    assertEquals(exactMatchAny("itemId", itemIds.subList(2, itemIds.size())).value()
      .and(openRequestsQuery.value()), actualQueries.get(1));
  }

  @Test
  void shouldKeepMergedOpenRequestsGloballyOrderedByPositionWhenChunked() {
    var clients = mock(Clients.class);
    var requestsStorageClient = mock(CollectionResourceClient.class);
    var requestsBatchStorageClient = mock(CollectionResourceClient.class);
    var cancellationReasonStorageClient = mock(CollectionResourceClient.class);

    when(clients.requestsStorage()).thenReturn(requestsStorageClient);
    when(clients.requestsBatchStorage()).thenReturn(requestsBatchStorageClient);
    when(clients.cancellationReasonStorage()).thenReturn(cancellationReasonStorageClient);
    when(requestsStorageClient.getMany(any(CqlQuery.class), any(PageLimit.class)))
      .thenReturn(
        completedFuture(succeeded(responseWithRequests(
          requestJson("request-2", "item-1", 2),
          requestJson("request-4", "item-2", 4)))),
        completedFuture(succeeded(responseWithRequests(
          requestJson("request-1", "item-3", 1),
          requestJson("request-3", "item-3", 3)))));

    var repository = new RequestRepository(clients);

    var result = repository.findOpenRequestsByItemIds(
      List.of("item-1", "item-2", "item-3"), 2).join();

    assertTrue(result.succeeded());
    assertEquals(List.of(1, 2, 3, 4), result.value().getRecords().stream()
      .map(Request::getPosition)
      .toList());
  }

  private ItemRepository createItemRepository(CollectionResourceClient itemsClient,
    CollectionResourceClient circulationItemClient) {

    var clients = mock(Clients.class);
    var collectionResourceMock = mock(CollectionResourceClient.class);
    var holdingsStorageClient = mock(CollectionResourceClient.class);
    var loanTypesStorageClient = mock(CollectionResourceClient.class);

    when(clients.servicePointsStorage()).thenReturn(collectionResourceMock);
    when(clients.locationsStorage()).thenReturn(collectionResourceMock);
    when(clients.institutionsStorage()).thenReturn(collectionResourceMock);
    when(clients.campusesStorage()).thenReturn(collectionResourceMock);
    when(clients.librariesStorage()).thenReturn(collectionResourceMock);
    when(clients.shadowLocationsStorage()).thenReturn(collectionResourceMock);
    when(clients.shadowInstitutionsStorage()).thenReturn(collectionResourceMock);
    when(clients.shadowCampusesStorage()).thenReturn(collectionResourceMock);
    when(clients.shadowLibrariesStorage()).thenReturn(collectionResourceMock);
    when(clients.materialTypesStorage()).thenReturn(collectionResourceMock);
    when(clients.instancesStorage()).thenReturn(collectionResourceMock);

    var servicePointRepository = new ServicePointRepository(clients);
    var locationRepository = LocationRepository.using(clients, servicePointRepository);
    var shadowLocationRepository = ShadowLocationRepository.using(clients, servicePointRepository);
    var materialTypeRepository = new MaterialTypeRepository(clients);
    var instanceRepository = new InstanceRepository(clients);
    var holdingsRepository = new HoldingsRepository(holdingsStorageClient);
    var loanTypeRepository = new LoanTypeRepository(loanTypesStorageClient);

    return new ItemRepository(itemsClient, locationRepository, shadowLocationRepository,
      materialTypeRepository, instanceRepository, holdingsRepository, loanTypeRepository,
      circulationItemClient);
  }

  private List<String> generateIds(int size) {
    return IntStream.range(0, size)
      .mapToObj(index -> randomUUID().toString())
      .toList();
  }

  private Response emptyResponse(String recordsPropertyName) {
    var body = new JsonObject()
      .put(recordsPropertyName, new JsonArray())
      .put("totalRecords", 0);

    return new Response(200, body.encode(), "application/json");
  }

  private Response responseWithItems(String... itemIds) {
    var items = new JsonArray();

    for (String itemId : itemIds) {
      items.add(new JsonObject().put("id", itemId));
    }

    return new Response(200, new JsonObject()
      .put("items", items)
      .put("totalRecords", itemIds.length)
      .encode(), "application/json");
  }

  private Response responseWithRequests(JsonObject... requests) {
    var requestArray = new JsonArray();

    for (JsonObject request : requests) {
      requestArray.add(request);
    }

    return new Response(200, new JsonObject()
      .put("requests", requestArray)
      .put("totalRecords", requests.length)
      .encode(), "application/json");
  }

  private JsonObject requestJson(String requestId, String itemId, int position) {
    return new JsonObject()
      .put("id", requestId)
      .put("itemId", itemId)
      .put("status", RequestStatus.OPEN_NOT_YET_FILLED.getValue())
      .put("position", position);
  }
}
