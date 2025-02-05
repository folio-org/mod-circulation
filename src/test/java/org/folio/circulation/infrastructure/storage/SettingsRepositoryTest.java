package org.folio.circulation.infrastructure.storage;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.SneakyThrows;

import org.folio.circulation.domain.configuration.TlrSettingsConfiguration;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.folio.circulation.support.results.Result.ofAsync;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SettingsRepositoryTest {

  @Test
  void testFetchSettingsWhenFeatureEnabled() throws ExecutionException, InterruptedException {
    Clients clients = mock(Clients.class);
    CollectionResourceClient collectionResourceClient = mock(CollectionResourceClient.class);
    when(clients.settingsStorageClient()).thenReturn(collectionResourceClient);
    SettingsRepository settingsRepository = new SettingsRepository(clients);
    when(collectionResourceClient.getMany(any(), any()))
      .thenReturn(CompletableFuture.completedFuture(Result.succeeded(new Response(200, createCheckoutLockJsonResponse(true).toString(), "application/json"))));
    var res = settingsRepository.lookUpCheckOutLockSettings().get().value();
    assertTrue(res.isCheckOutLockFeatureEnabled());
  }

  @Test
  void testFetchSettingsWhenFeatureDisabled() throws ExecutionException, InterruptedException {
    Clients clients = mock(Clients.class);
    CollectionResourceClient collectionResourceClient = mock(CollectionResourceClient.class);
    when(clients.settingsStorageClient()).thenReturn(collectionResourceClient);
    SettingsRepository settingsRepository = new SettingsRepository(clients);
    when(collectionResourceClient.getMany(any(), any()))
      .thenReturn(CompletableFuture.completedFuture(Result.succeeded(new Response(200, createCheckoutLockJsonResponse(false).toString(), "application/json"))));
    var res = settingsRepository.lookUpCheckOutLockSettings().get().value();
    assertFalse(res.isCheckOutLockFeatureEnabled());
  }

  @Test
  void testFetchSettingsWhenSettingsApiThrowError() throws ExecutionException, InterruptedException {
    Clients clients = mock(Clients.class);
    CollectionResourceClient collectionResourceClient = mock(CollectionResourceClient.class);
    when(clients.settingsStorageClient()).thenReturn(collectionResourceClient);
    SettingsRepository settingsRepository = new SettingsRepository(clients);
    when(collectionResourceClient.getMany(any(), any()))
      .thenReturn(CompletableFuture.completedFuture(Result.failed(new ServerErrorFailure("Unable to call mod settings"))));
    var res = settingsRepository.lookUpCheckOutLockSettings().get().value();
    assertFalse(res.isCheckOutLockFeatureEnabled());
  }

  @Test
  @SneakyThrows
  void fetchTlrSettings() {
    Clients clients = mock(Clients.class);
    CollectionResourceClient settingsClient = mock(CollectionResourceClient.class);
    CollectionResourceClient configurationClient = mock(CollectionResourceClient.class);

    JsonObject mockSettingsResponse = new JsonObject()
      .put("items", new JsonArray()
        .add(new JsonObject()
          .put("id", UUID.randomUUID().toString())
          .put("scope", "circulation")
          .put("key", "generalTlr")
          .put("value", new JsonObject()
            .put("titleLevelRequestsFeatureEnabled", true)
            .put("createTitleLevelRequestsByDefault", true)
            .put("tlrHoldShouldFollowCirculationRules", true))))
      .put("resultInfo", new JsonObject()
        .put("totalRecords", 0)
        .put("diagnostics", new JsonArray()));

    when(clients.settingsStorageClient()).thenReturn(settingsClient);
    when(clients.configurationStorageClient()).thenReturn(configurationClient);
    when(settingsClient.getMany(any(), any()))
      .thenReturn(ofAsync(new Response(200, mockSettingsResponse.encode(), "application/json")));

    TlrSettingsConfiguration actualResult = new SettingsRepository(clients)
      .lookupTlrSettings()
      .get(30, TimeUnit.SECONDS)
      .value();

    assertEquals(new TlrSettingsConfiguration(true, true, true, null, null, null), actualResult);
    verify(settingsClient).getMany(any(), any());
    verifyNoInteractions(configurationClient);
  }

  @Test
  @SneakyThrows
  void fallBackToLegacyConfigurationWhenTlrSettingsAreNotFound() {
    Clients clients = mock(Clients.class);
    CollectionResourceClient settingsClient = mock(CollectionResourceClient.class);
    CollectionResourceClient configurationClient = mock(CollectionResourceClient.class);

    JsonObject mockEmptySettingsResponse = new JsonObject()
      .put("items", new JsonArray())
      .put("resultInfo", new JsonObject()
        .put("totalRecords", 0)
        .put("diagnostics", new JsonArray()));

    JsonObject mockConfigurationResponse = new JsonObject()
      .put("configs", new JsonArray().add(
        new JsonObject()
          .put("id", UUID.randomUUID().toString())
          .put("module", "SETTINGS")
          .put("configName", "TLR")
          .put("enabled", true)
          .put("value", new JsonObject()
            .put("titleLevelRequestsFeatureEnabled", true)
            .put("createTitleLevelRequestsByDefault", true)
            .put("tlrHoldShouldFollowCirculationRules", true)
            .put("confirmationPatronNoticeTemplateId", null)
            .put("cancellationPatronNoticeTemplateId", null)
            .put("expirationPatronNoticeTemplateId", null)
            .encode())))
      .put("totalRecords", 1)
      .put("resultInfo", new JsonObject()
        .put("totalRecords", 1)
        .put("facets", new JsonArray())
        .put("diagnostics", new JsonArray()));

    when(clients.settingsStorageClient()).thenReturn(settingsClient);
    when(clients.configurationStorageClient()).thenReturn(configurationClient);
    when(settingsClient.getMany(any(), any()))
      .thenReturn(ofAsync(new Response(200, mockEmptySettingsResponse.encode(), "application/json")));
    when(configurationClient.getMany(any(), any()))
      .thenReturn(ofAsync(new Response(200, mockConfigurationResponse.encode(), "application/json")));

    TlrSettingsConfiguration actualResult = new SettingsRepository(clients)
      .lookupTlrSettings()
      .get(30, TimeUnit.SECONDS)
      .value();

    assertEquals(new TlrSettingsConfiguration(true, true, true, null, null, null), actualResult);
    verify(settingsClient).getMany(any(), any());
    verify(configurationClient).getMany(any(), any());
  }

  private JsonObject createCheckoutLockJsonResponse(boolean checkoutFeatureFlag) {
    JsonObject checkoutLockResponseJson = new JsonObject();
    checkoutLockResponseJson.put("id", UUID.randomUUID())
      .put("scope", "mod-circulation")
      .put("key", "checkoutLockFeature")
      .put("value",
        new JsonObject().put("checkOutLockFeatureEnabled", checkoutFeatureFlag)
          .put("lockTtl", 500)
          .put("retryInterval", 5)
          .put("noOfRetryAttempts", 10)
          .encodePrettily()
      ).encodePrettily();
    JsonObject result = new JsonObject();
    result.put("items", new JsonArray(List.of(checkoutLockResponseJson)));
    return result;
  }
}
