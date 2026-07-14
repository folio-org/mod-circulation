package org.folio.circulation.infrastructure.storage.inventory;

import static api.support.matchers.ResultMatchers.succeeded;
import static org.folio.circulation.infrastructure.storage.inventory.LocationRepository.using;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Location;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.storage.mappers.ItemMapper;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.SneakyThrows;
import lombok.val;

class LocationRepositoryTest {

  @Test
  void shouldReturnUnknownLocationWhenLocationIdIsNull() {
    final LocationRepository repository = using(mock(Clients.class),
      new ServicePointRepository(mock(Clients.class)));

    val result = repository.fetchLocationById(null)
      .getNow(Result.failed(new ServerErrorFailure("Error")));

    assertTrue(result.succeeded());
    assertThat(result.value(), is(instanceOf(Location.class)));
    assertThat(result.value().getId(), is(nullValue()));
  }

  @Test
  void getEffectiveLocationShouldUseEffectiveLocationPrimaryServicePointNameWithoutFetchingServicePoint() {
    final var dcbSpId = UUID.randomUUID().toString();
    final var realSpName = "Circ Desk 1";
    final var locationId = UUID.randomUUID().toString();

    final var locationJson = new JsonObject()
      .put("id", locationId)
      .put("name", "ECS Shadow Location")
      .put("code", "ECS-SL")
      .put("primaryServicePoint", dcbSpId)
      .put("effectiveLocationPrimaryServicePointName", realSpName)
      .put("servicePointIds", new JsonArray().add(dcbSpId));

    final var locationsClient = mock(CollectionResourceClient.class);
    final var servicePointRepository = mock(ServicePointRepository.class);

    when(locationsClient.get(anyString())).thenReturn(ofAsync(
      () -> new Response(200, locationJson.encodePrettily(), "application/json")));

    final var clients = mock(Clients.class);
    when(clients.locationsStorage()).thenReturn(locationsClient);
    when(clients.institutionsStorage()).thenReturn(mock(CollectionResourceClient.class));
    when(clients.campusesStorage()).thenReturn(mock(CollectionResourceClient.class));
    when(clients.librariesStorage()).thenReturn(mock(CollectionResourceClient.class));

    final var repository = using(clients, servicePointRepository);

    final var itemJson = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("holdingsRecordId", UUID.randomUUID().toString())
      .put("effectiveLocationId", locationId);
    final Item item = new ItemMapper().toDomain(itemJson);

    final Result<Location> result = get(repository.getEffectiveLocation(item));

    assertThat(result, succeeded());
    assertThat(result.value().getPrimaryServicePoint().getName(), is(realSpName));
    verify(servicePointRepository, never()).getServicePointById(any(UUID.class));
    verify(servicePointRepository, never()).getServicePointById(anyString());
  }

  @SneakyThrows
  private <T> Result<T> get(CompletableFuture<Result<T>> future) {
    return future.get(1, TimeUnit.SECONDS);
  }
}
