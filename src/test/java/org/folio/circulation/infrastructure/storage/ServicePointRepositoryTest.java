package org.folio.circulation.infrastructure.storage;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang.StringUtils;
import org.folio.circulation.domain.*;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.GetManyRecordsClient;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServicePointRepositoryTest {

  @Test
  void findPrimaryServicePointsForRequestsTest() throws Exception {
    UUID primaryServicePointId = UUID.randomUUID();
    String name = "test name";

    Request request = mock(Request.class);
    Item item = mock(Item.class);
    Institution institution = new Institution(StringUtils.EMPTY, StringUtils.EMPTY);
    Campus campus = new Campus(StringUtils.EMPTY, StringUtils.EMPTY);
    Library library = new Library(StringUtils.EMPTY, StringUtils.EMPTY);

    ServicePoint primaryServicePoint = ServicePoint.unknown(primaryServicePointId.toString(), name);
    Location location = new Location("11", null, null, null, List.of(UUID.randomUUID()), primaryServicePointId, institution, campus, library, primaryServicePoint);
    when(request.getItem()).thenReturn(item);
    when(item.getLocation()).thenReturn(location);
    when(item.withLocation(any())).thenReturn(item);
    when(request.withItem(any())).thenReturn(request);

    CollectionResourceClient client = mock(CollectionResourceClient.class);
    MultipleRecords<Request> multipleRequests = new MultipleRecords<>(List.of(request), 1);
    Clients clients = mock(Clients.class);
    when(clients.servicePointsStorage()).thenReturn(client);
    JsonObject automatedPatronBlocks = new JsonObject()
      .put("servicepoints", new JsonArray(Arrays.asList(
        new JsonObject().put("id", primaryServicePointId.toString())
          .put("name", name)
      )))
      .put("totalRecords", 1);

    mockedClientGet(client, automatedPatronBlocks.encodePrettily());
    ServicePointRepository servicePointRepository = new ServicePointRepository(clients);
    CompletableFuture<Result<MultipleRecords<Request>>> result =  servicePointRepository.findPrimaryServicePointsForRequests(multipleRequests);

    MultipleRecords<Request> mRecords = result.get().value();
    Collection<Request> coll = mRecords.getRecords();

    Request receivedRequest = coll.stream().findFirst().get();
    assertEquals(name, receivedRequest.getItem().getLocation().getPrimaryServicePoint().getName());
  }

  @Test
  void findPrimaryServicePointsForRequests_mismatchTest() throws Exception {
    UUID primaryServicePointId = UUID.randomUUID();
    String name = "test name";

    Request request = mock(Request.class);
    Item item = mock(Item.class);
    Institution institution = new Institution(StringUtils.EMPTY, StringUtils.EMPTY);
    Campus campus = new Campus(StringUtils.EMPTY, StringUtils.EMPTY);
    Library library = new Library(StringUtils.EMPTY, StringUtils.EMPTY);

    ServicePoint primaryServicePoint = ServicePoint.unknown(primaryServicePointId.toString(), name);
    Location location = new Location("11", null, null, null, List.of(UUID.randomUUID()), primaryServicePointId, institution, campus, library, primaryServicePoint);
    when(request.getItem()).thenReturn(item);
    when(item.getLocation()).thenReturn(location);

    CollectionResourceClient client = mock(CollectionResourceClient.class);
    MultipleRecords<Request> multipleRequests = new MultipleRecords<>(List.of(request), 1);
    Clients clients = mock(Clients.class);
    when(clients.servicePointsStorage()).thenReturn(client);
    JsonObject automatedPatronBlocks = new JsonObject()
      .put("servicepoints", new JsonArray(Arrays.asList(
        new JsonObject().put("id", UUID.randomUUID())
          .put("name", name)
      )))
      .put("totalRecords", 1);

    mockedClientGet(client, automatedPatronBlocks.encodePrettily());
    ServicePointRepository servicePointRepository = new ServicePointRepository(clients);
    CompletableFuture<Result<MultipleRecords<Request>>> result =  servicePointRepository.findPrimaryServicePointsForRequests(multipleRequests);

    verify(item, never()).withLocation(any());
  }

  private void mockedClientGet(GetManyRecordsClient client, String body) {
    when(client.getMany(any(), any())).thenReturn(Result.ofAsync(
      () -> new Response(200, body, "application/json")));
  }
}
