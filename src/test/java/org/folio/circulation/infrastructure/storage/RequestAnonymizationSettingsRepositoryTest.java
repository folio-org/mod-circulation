package org.folio.circulation.infrastructure.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.isNull;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.anonymization.RequestAnonymizationSettings;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

class RequestAnonymizationSettingsRepositoryTest {

  @Test
  void returnsDefaultWhenNoRowFound() {
    CollectionResourceClient client = mock(CollectionResourceClient.class);

    Response response = mock(Response.class);
    //CqlQuery query = mock(CqlQuery.class);
    when(response.getJson()).thenReturn(new JsonObject()
      .put("circulationSettings", new io.vertx.core.json.JsonArray())
      .put("totalRecords", 0));

    when(client.getMany(any(CqlQuery.class), isNull()))
      .thenReturn(CompletableFuture.completedFuture(Result.succeeded(response)));

    RequestAnonymizationSettingsRepository repo = new RequestAnonymizationSettingsRepository(client);

    RequestAnonymizationSettings settings = repo.getSettings().join().value();

    assertEquals(RequestAnonymizationSettings.defaultSettings().toString(), settings.toString());
  }

  @Test
  void returnsParsedSettingsWhenRowExists() {
    CollectionResourceClient client = mock(CollectionResourceClient.class);

    JsonObject row = new JsonObject()
      .put("name", "requestAnonymization")
      .put("value", new JsonObject()
        .put("mode", "delayed")
        .put("delay", 7)
        .put("delayUnit", "days"));

    JsonObject body = new JsonObject()
      .put("circulationSettings", new io.vertx.core.json.JsonArray().add(row))
      .put("totalRecords", 1);

    Response response = mock(Response.class);
    CqlQuery query = mock(CqlQuery.class);
    when(response.getJson()).thenReturn(body);

    when(client.getMany(any(CqlQuery.class), isNull()))
      .thenReturn(CompletableFuture.completedFuture(Result.succeeded(response)));

    RequestAnonymizationSettingsRepository repo = new RequestAnonymizationSettingsRepository(client);

    RequestAnonymizationSettings settings = repo.getSettings().join().value();

    assertTrue(settings.isDelayed());
    assertEquals(7, settings.getDelay());
    assertEquals(RequestAnonymizationSettings.DelayUnit.DAYS, settings.getDelayUnit());
  }

  @Test
  void returnsDefaultWhenBodyIsNull() {
    CollectionResourceClient client = mock(CollectionResourceClient.class);

    Response response = mock(Response.class);
    CqlQuery query = mock(CqlQuery.class);
    when(response.getJson()).thenReturn(null);

    when(client.getMany(any(CqlQuery.class), isNull()))
      .thenReturn(CompletableFuture.completedFuture(Result.succeeded(response)));

    RequestAnonymizationSettingsRepository repo = new RequestAnonymizationSettingsRepository(client);

    RequestAnonymizationSettings settings = repo.getSettings().join().value();

    assertEquals(RequestAnonymizationSettings.defaultSettings().toString(), settings.toString());
  }

  @Test
  void clientsConstructorUsesCirculationSettingsStorageClient() {
    Clients clients = mock(Clients.class);
    CollectionResourceClient client = mock(CollectionResourceClient.class);

    when(clients.circulationSettingsStorageClient()).thenReturn(client);

    RequestAnonymizationSettingsRepository repo = new RequestAnonymizationSettingsRepository(clients);

    Response response = mock(Response.class);
    when(response.getJson()).thenReturn(new JsonObject()
      .put("circulationSettings", new io.vertx.core.json.JsonArray()));
    when(client.getMany(any(CqlQuery.class), isNull()))
      .thenReturn(CompletableFuture.completedFuture(Result.succeeded(response)));

    RequestAnonymizationSettings settings = repo.getSettings().join().value();
    assertEquals(RequestAnonymizationSettings.defaultSettings().toString(), settings.toString());
  }

  @Test
  void returnsDefaultWhenResponseIsNull() {
    CollectionResourceClient client = mock(CollectionResourceClient.class);

    when(client.getMany(any(CqlQuery.class), isNull()))
      .thenReturn(CompletableFuture.completedFuture(Result.succeeded(null)));

    RequestAnonymizationSettingsRepository repo = new RequestAnonymizationSettingsRepository(client);

    RequestAnonymizationSettings settings = repo.getSettings().join().value();

    assertEquals(RequestAnonymizationSettings.defaultSettings().toString(), settings.toString());
  }


}
