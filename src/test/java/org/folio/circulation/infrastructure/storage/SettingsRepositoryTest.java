package org.folio.circulation.infrastructure.storage;

import static org.folio.circulation.support.results.Result.ofAsync;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.SneakyThrows;

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
  void fetchTenantLocaleSettingsFallsBackToDefaultWhenLocaleEndpointFails() {
    Clients clients = mock(Clients.class);
    CollectionResourceClient localeClient = mock(CollectionResourceClient.class);

    when(clients.localeClient()).thenReturn(localeClient);
    when(localeClient.get()).thenReturn(ofAsync(new Response(404, "", "application/json")));

    ZoneId actualResult = new SettingsRepository(clients)
      .lookupTimeZoneSettings()
      .get(30, TimeUnit.SECONDS)
      .value();

    assertEquals(ZoneOffset.UTC, actualResult);
    verify(localeClient).get();
  }

  @Test
  @SneakyThrows
  void fetchTimezoneFromLocaleEndpoint() {
    Clients clients = mock(Clients.class);
    CollectionResourceClient localeClient = mock(CollectionResourceClient.class);

    JsonObject mockLocaleResponse = new JsonObject()
      .put("locale", "en-US")
      .put("timezone", "America/New_York")
      .put("currency", "USD");

    when(clients.localeClient()).thenReturn(localeClient);
    when(localeClient.get())
      .thenReturn(ofAsync(new Response(200, mockLocaleResponse.encode(), "application/json")));

    ZoneId actualResult = new SettingsRepository(clients)
      .lookupTimeZoneSettings()
      .get(30, TimeUnit.SECONDS)
      .value();

    assertEquals(ZoneId.of("America/New_York"), actualResult);
    verify(localeClient).get();
  }

  @Test
  @SneakyThrows
  void fetchTimezoneFromLocaleEndpointWithNumberingSystem() {
    Clients clients = mock(Clients.class);
    CollectionResourceClient localeClient = mock(CollectionResourceClient.class);

    JsonObject mockLocaleResponse = new JsonObject()
      .put("locale", "ar-SA")
      .put("timezone", "Asia/Riyadh")
      .put("currency", "SAR")
      .put("numberingSystem", "arab");

    when(clients.localeClient()).thenReturn(localeClient);
    when(localeClient.get())
      .thenReturn(ofAsync(new Response(200, mockLocaleResponse.encode(), "application/json")));

    ZoneId actualResult = new SettingsRepository(clients)
      .lookupTimeZoneSettings()
      .get(30, TimeUnit.SECONDS)
      .value();

    assertEquals(ZoneId.of("Asia/Riyadh"), actualResult);
    verify(localeClient).get();
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
