package org.folio.circulation.infrastructure.storage;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
