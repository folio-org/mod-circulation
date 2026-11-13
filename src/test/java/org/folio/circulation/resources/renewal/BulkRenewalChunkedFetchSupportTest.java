package org.folio.circulation.resources.renewal;

import static org.folio.circulation.support.CqlSortBy.ascending;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatchAny;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.MultipleRecords;
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
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class BulkRenewalChunkedFetchSupportTest {

  @Test
  void shouldPartitionValuesIntoConfiguredChunks() {
    assertEquals(List.of(List.of("1", "2"), List.of("3")),
      BulkRenewalChunkedFetchSupport.partition(List.of("1", "2", "3"), 2));
  }

  @Test
  void shouldPreserveInputOrderAcrossChunks() {
    assertEquals(List.of(List.of("c", "a"), List.of("b")),
      BulkRenewalChunkedFetchSupport.partition(List.of("c", "a", "b"), 2));
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
      BulkRenewalChunkedFetchSupport.partition(values, 2));
  }

  @Test
  void shouldExposeItemIdChunkSizeForBulkRenewalFetches() {
    assertEquals(80, BulkRenewalChunkedFetchSupport.ITEM_ID_CHUNK_SIZE);
  }

  @Test
  void shouldCreateSortedOpenLoanPageQuery() {
    Clients clients = mock(Clients.class);
    when(clients.loansStorage()).thenReturn(mock(CollectionResourceClient.class));
    when(clients.loansHistoryStorageClient()).thenReturn(mock(CollectionResourceClient.class));

    LoanRepository repository = new LoanRepository(clients,
      mock(ItemRepository.class), mock(UserRepository.class));

    Result<CqlQuery> result = repository.createOpenLoanPageQuery();

    assertTrue(result.succeeded());
    assertEquals(exactMatch("status.name", "Open")
      .map(query -> query.sortBy(ascending("id"))).value(), result.value());
  }

  @Test
  void shouldFetchOpenLoanPageUsingSortedQueryAndOffset() {
    Clients clients = mock(Clients.class);
    CollectionResourceClient loansStorageClient = mock(CollectionResourceClient.class);

    when(clients.loansStorage()).thenReturn(loansStorageClient);
    when(clients.loansHistoryStorageClient()).thenReturn(mock(CollectionResourceClient.class));
    when(loansStorageClient.getMany(any(CqlQuery.class), any(PageLimit.class), any(Offset.class)))
      .thenReturn(CompletableFuture.completedFuture(Result.succeeded(emptyResponse("loans"))));

    LoanRepository repository = new LoanRepository(clients,
      mock(ItemRepository.class), mock(UserRepository.class));
    PageLimit pageLimit = PageLimit.limit(5);
    Offset offset = Offset.offset(10);

    Result<MultipleRecords<Loan>> result = repository.findOpenLoans(pageLimit, offset).join();

    assertTrue(result.succeeded());

    ArgumentCaptor<CqlQuery> queryCaptor = ArgumentCaptor.forClass(CqlQuery.class);
    verify(loansStorageClient).getMany(queryCaptor.capture(), eq(pageLimit), eq(offset));

    assertEquals(exactMatch("status.name", "Open")
      .map(query -> query.sortBy(ascending("id"))).value(), queryCaptor.getValue());
  }

  @Test
  void shouldFetchItemsUsingConfiguredChunkSize() {
    CollectionResourceClient itemsClient = mock(CollectionResourceClient.class);
    CollectionResourceClient circulationItemClient = mock(CollectionResourceClient.class);

    when(itemsClient.getMany(any(CqlQuery.class), any(PageLimit.class)))
      .thenReturn(CompletableFuture.completedFuture(Result.succeeded(emptyResponse("items"))));
    when(circulationItemClient.getMany(any(CqlQuery.class), any(PageLimit.class)))
      .thenReturn(CompletableFuture.completedFuture(Result.succeeded(emptyResponse("items"))));

    ItemRepository repository = createItemRepository(itemsClient, circulationItemClient);
    List<String> itemIds = generateIds(BulkRenewalChunkedFetchSupport.ITEM_ID_CHUNK_SIZE + 1);

    Result<MultipleRecords<Item>> result = repository.fetchFor(itemIds,
      BulkRenewalChunkedFetchSupport.ITEM_ID_CHUNK_SIZE).join();

    assertTrue(result.succeeded());

    ArgumentCaptor<CqlQuery> queryCaptor = ArgumentCaptor.forClass(CqlQuery.class);
    verify(itemsClient, times(2)).getMany(queryCaptor.capture(), any(PageLimit.class));

    List<CqlQuery> actualQueries = queryCaptor.getAllValues();

    assertEquals(exactMatchAny("id", itemIds.subList(0,
      BulkRenewalChunkedFetchSupport.ITEM_ID_CHUNK_SIZE)).value(), actualQueries.get(0));
    assertEquals(exactMatchAny("id", itemIds.subList(
      BulkRenewalChunkedFetchSupport.ITEM_ID_CHUNK_SIZE, itemIds.size())).value(),
      actualQueries.get(1));
  }

  @Test
  void shouldNormalizeItemIdsBeforeChunkedFetchCompletenessCheck() {
    CollectionResourceClient itemsClient = mock(CollectionResourceClient.class);
    CollectionResourceClient circulationItemClient = mock(CollectionResourceClient.class);
    String itemId = UUID.randomUUID().toString();

    when(itemsClient.getMany(any(CqlQuery.class), any(PageLimit.class)))
      .thenReturn(CompletableFuture.completedFuture(Result.succeeded(responseWithItems(itemId))));

    ItemRepository repository = createItemRepository(itemsClient, circulationItemClient);
    List<String> itemIds = new ArrayList<>();

    IntStream.range(0, BulkRenewalChunkedFetchSupport.ITEM_ID_CHUNK_SIZE + 1)
      .forEach(index -> itemIds.add(itemId));

    itemIds.add("");
    itemIds.add("   ");
    itemIds.add(null);

    Result<MultipleRecords<Item>> result = repository.fetchFor(itemIds,
      BulkRenewalChunkedFetchSupport.ITEM_ID_CHUNK_SIZE).join();

    assertTrue(result.succeeded());
    assertEquals(1, result.value().size());

    ArgumentCaptor<CqlQuery> queryCaptor = ArgumentCaptor.forClass(CqlQuery.class);
    verify(itemsClient, times(1)).getMany(queryCaptor.capture(), any(PageLimit.class));
    verify(circulationItemClient, times(0)).getMany(any(CqlQuery.class), any(PageLimit.class));

    assertEquals(exactMatchAny("id", List.of(itemId)).value(), queryCaptor.getValue());
  }

  @Test
  void shouldFetchUsersUsingConfiguredChunkSize() {
    Clients clients = mock(Clients.class);
    CollectionResourceClient usersStorageClient = mock(CollectionResourceClient.class);

    when(clients.usersStorage()).thenReturn(usersStorageClient);
    when(clients.patronGroupsStorage()).thenReturn(mock(CollectionResourceClient.class));
    when(clients.addressTypesStorage()).thenReturn(mock(CollectionResourceClient.class));
    when(usersStorageClient.getMany(any(CqlQuery.class), any(PageLimit.class)))
      .thenReturn(CompletableFuture.completedFuture(Result.succeeded(emptyResponse("users"))));

    UserRepository repository = new UserRepository(clients);
    List<String> userIds = generateIds(3);

    var result = repository.getUsersForUserIds(userIds, 2).join();

    assertTrue(result.succeeded());

    ArgumentCaptor<CqlQuery> queryCaptor = ArgumentCaptor.forClass(CqlQuery.class);
    verify(usersStorageClient, times(2)).getMany(queryCaptor.capture(), any(PageLimit.class));

    List<CqlQuery> actualQueries = queryCaptor.getAllValues();

    assertEquals(exactMatchAny("id", userIds.subList(0, 2)).value(), actualQueries.get(0));
    assertEquals(exactMatchAny("id", userIds.subList(2, userIds.size())).value(),
      actualQueries.get(1));
  }

  @Test
  void shouldFetchOpenRequestsUsingConfiguredChunkSize() {
    Clients clients = mock(Clients.class);
    CollectionResourceClient requestsStorageClient = mock(CollectionResourceClient.class);

    when(clients.requestsStorage()).thenReturn(requestsStorageClient);
    when(clients.requestsBatchStorage()).thenReturn(mock(CollectionResourceClient.class));
    when(clients.cancellationReasonStorage()).thenReturn(mock(CollectionResourceClient.class));
    when(requestsStorageClient.getMany(any(CqlQuery.class), any(PageLimit.class)))
      .thenReturn(CompletableFuture.completedFuture(Result.succeeded(emptyResponse("requests"))));

    RequestRepository repository = new RequestRepository(clients);
    List<String> itemIds = generateIds(3);

    Result<MultipleRecords<Request>> result = repository.findOpenRequestsByItemIds(itemIds, 2).join();

    assertTrue(result.succeeded());

    ArgumentCaptor<CqlQuery> queryCaptor = ArgumentCaptor.forClass(CqlQuery.class);
    verify(requestsStorageClient, times(2)).getMany(queryCaptor.capture(), any(PageLimit.class));

    Result<CqlQuery> openRequestsQuery = exactMatchAny("status", RequestStatus.openStates())
      .map(query -> query.sortBy(ascending("position")));

    List<CqlQuery> actualQueries = queryCaptor.getAllValues();

    assertEquals(exactMatchAny("itemId", itemIds.subList(0, 2)).value()
      .and(openRequestsQuery.value()), actualQueries.get(0));
    assertEquals(exactMatchAny("itemId", itemIds.subList(2, itemIds.size())).value()
      .and(openRequestsQuery.value()), actualQueries.get(1));
  }

  @Test
  void shouldKeepMergedOpenRequestsGloballyOrderedByPositionWhenChunked() {
    Clients clients = mock(Clients.class);
    CollectionResourceClient requestsStorageClient = mock(CollectionResourceClient.class);

    when(clients.requestsStorage()).thenReturn(requestsStorageClient);
    when(clients.requestsBatchStorage()).thenReturn(mock(CollectionResourceClient.class));
    when(clients.cancellationReasonStorage()).thenReturn(mock(CollectionResourceClient.class));
    when(requestsStorageClient.getMany(any(CqlQuery.class), any(PageLimit.class)))
      .thenReturn(
        CompletableFuture.completedFuture(Result.succeeded(responseWithRequests(
          requestJson("request-2", "item-1", 2),
          requestJson("request-4", "item-2", 4)))),
        CompletableFuture.completedFuture(Result.succeeded(responseWithRequests(
          requestJson("request-1", "item-3", 1),
          requestJson("request-3", "item-3", 3)))));

    RequestRepository repository = new RequestRepository(clients);

    Result<MultipleRecords<Request>> result = repository.findOpenRequestsByItemIds(
      List.of("item-1", "item-2", "item-3"), 2).join();

    assertTrue(result.succeeded());
    assertEquals(List.of(1, 2, 3, 4), result.value().getRecords().stream()
      .map(Request::getPosition)
      .toList());
  }

  private ItemRepository createItemRepository(CollectionResourceClient itemsClient,
    CollectionResourceClient circulationItemClient) {

    Clients clients = mock(Clients.class);

    when(clients.servicePointsStorage()).thenReturn(mock(CollectionResourceClient.class));
    when(clients.locationsStorage()).thenReturn(mock(CollectionResourceClient.class));
    when(clients.institutionsStorage()).thenReturn(mock(CollectionResourceClient.class));
    when(clients.campusesStorage()).thenReturn(mock(CollectionResourceClient.class));
    when(clients.librariesStorage()).thenReturn(mock(CollectionResourceClient.class));
    when(clients.shadowLocationsStorage()).thenReturn(mock(CollectionResourceClient.class));
    when(clients.shadowInstitutionsStorage()).thenReturn(mock(CollectionResourceClient.class));
    when(clients.shadowCampusesStorage()).thenReturn(mock(CollectionResourceClient.class));
    when(clients.shadowLibrariesStorage()).thenReturn(mock(CollectionResourceClient.class));
    when(clients.materialTypesStorage()).thenReturn(mock(CollectionResourceClient.class));
    when(clients.instancesStorage()).thenReturn(mock(CollectionResourceClient.class));

    ServicePointRepository servicePointRepository = new ServicePointRepository(clients);
    LocationRepository locationRepository = LocationRepository.using(clients, servicePointRepository);
    ShadowLocationRepository shadowLocationRepository = ShadowLocationRepository.using(clients,
      servicePointRepository);
    MaterialTypeRepository materialTypeRepository = new MaterialTypeRepository(clients);
    InstanceRepository instanceRepository = new InstanceRepository(clients);
    HoldingsRepository holdingsRepository = new HoldingsRepository(mock(CollectionResourceClient.class));
    LoanTypeRepository loanTypeRepository = new LoanTypeRepository(mock(CollectionResourceClient.class));

    return new ItemRepository(itemsClient, locationRepository, shadowLocationRepository,
      materialTypeRepository, instanceRepository, holdingsRepository, loanTypeRepository,
      circulationItemClient);
  }

  private List<String> generateIds(int size) {
    return IntStream.range(0, size)
      .mapToObj(index -> UUID.randomUUID().toString())
      .toList();
  }

  private Response emptyResponse(String recordsPropertyName) {
    JsonObject body = new JsonObject()
      .put(recordsPropertyName, new JsonArray())
      .put("totalRecords", 0);

    return new Response(200, body.encode(), "application/json");
  }

  private Response responseWithItems(String... itemIds) {
    JsonArray items = new JsonArray();

    for (String itemId : itemIds) {
      items.add(new JsonObject().put("id", itemId));
    }

    return new Response(200, new JsonObject()
      .put("items", items)
      .put("totalRecords", itemIds.length)
      .encode(), "application/json");
  }

  private Response responseWithRequests(JsonObject... requests) {
    JsonArray requestArray = new JsonArray();

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
