package org.folio.circulation.infrastructure.storage;

import static org.folio.circulation.support.results.Result.ofAsync;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.folio.circulation.domain.configuration.TlrSettingsConfiguration;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.client.Response;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.http.MimeType;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.SneakyThrows;

class CirculationSettingsRepositoryTest {

  @Test
  @SneakyThrows
  void fetchTlrSettings() {
    Clients clients = mock(Clients.class);
    CollectionResourceClient circulationSettingsClient = mock(CollectionResourceClient.class);

    JsonObject mockSettingsResponse = new JsonObject()
      .put("circulationSettings", new JsonArray()
        .add(new JsonObject()
          .put("id", UUID.randomUUID().toString())
          .put("name", "generalTlr")
          .put("value", new JsonObject()
            .put("titleLevelRequestsFeatureEnabled", true)
            .put("createTitleLevelRequestsByDefault", false)
            .put("tlrHoldShouldFollowCirculationRules", true)))
        .add(new JsonObject()
          .put("id", UUID.randomUUID().toString())
          .put("name", "regularTlr")
          .put("value", new JsonObject()
            .put("confirmationPatronNoticeTemplateId", "51958757-df1d-4c71-84d3-820575d73f81")
            .put("cancellationPatronNoticeTemplateId", "51958757-df1d-4c71-84d3-820575d73f82")
            .put("expirationPatronNoticeTemplateId", "51958757-df1d-4c71-84d3-820575d73f83"))))
      .put("totalRecords", 2);

    when(clients.circulationSettingsStorageClient()).thenReturn(circulationSettingsClient);
    when(circulationSettingsClient.getMany(any(), any()))
      .thenReturn(ofAsync(new Response(200, mockSettingsResponse.encode(), MimeType.JSON.toString())));

    TlrSettingsConfiguration actualResult = new CirculationSettingsRepository(clients)
      .getTlrSettings()
      .get(30, TimeUnit.SECONDS)
      .value();

    TlrSettingsConfiguration expectedResult = new TlrSettingsConfiguration(true, false, true,
      UUID.fromString("51958757-df1d-4c71-84d3-820575d73f81"),
      UUID.fromString("51958757-df1d-4c71-84d3-820575d73f82"),
      UUID.fromString("51958757-df1d-4c71-84d3-820575d73f83"));

    assertEquals(expectedResult, actualResult);
    verify(circulationSettingsClient).getMany(any(), any());
  }

  @Test
  @SneakyThrows
  void fallBackToLegacySettingsWhenTlrSettingsAreNotFound() {
    Clients clients = mock(Clients.class);
    CollectionResourceClient circulationSettingsClient = mock(CollectionResourceClient.class);

    JsonObject mockResponse = new JsonObject()
      .put("circulationSettings", new JsonArray().add(
        new JsonObject()
          .put("id", UUID.randomUUID().toString())
          .put("name", "TLR")
          .put("value", new JsonObject()
            .put("titleLevelRequestsFeatureEnabled", true)
            .put("createTitleLevelRequestsByDefault", true)
            .put("tlrHoldShouldFollowCirculationRules", true)
            .put("confirmationPatronNoticeTemplateId", null)
            .put("cancellationPatronNoticeTemplateId", null)
            .put("expirationPatronNoticeTemplateId", null))))
      .put("totalRecords", 1);

    when(clients.circulationSettingsStorageClient()).thenReturn(circulationSettingsClient);
    when(circulationSettingsClient.getMany(any(), any()))
      .thenReturn(ofAsync(new Response(200, mockResponse.encode(), MimeType.JSON.toString())));

    TlrSettingsConfiguration actualResult = new CirculationSettingsRepository(clients)
      .getTlrSettings()
      .get(30, TimeUnit.SECONDS)
      .value();

    assertEquals(new TlrSettingsConfiguration(true, true, true, null, null, null), actualResult);
    verify(circulationSettingsClient).getMany(any(), any());
  }

  @Test
  @SneakyThrows
  void fallBackToDefaultSettingsWhenNoTlrSettingsAreFound() {
    Clients clients = mock(Clients.class);
    CollectionResourceClient circulationSettingsClient = mock(CollectionResourceClient.class);

    JsonObject mockResponse = new JsonObject()
      .put("circulationSettings", new JsonArray())
      .put("totalRecords", 0);

    when(clients.circulationSettingsStorageClient()).thenReturn(circulationSettingsClient);
    when(circulationSettingsClient.getMany(any(), any()))
      .thenReturn(ofAsync(new Response(200, mockResponse.encode(), MimeType.JSON.toString())));

    TlrSettingsConfiguration actualResult = new CirculationSettingsRepository(clients)
      .getTlrSettings()
      .get(30, TimeUnit.SECONDS)
      .value();

    assertEquals(new TlrSettingsConfiguration(false, false, false, null, null, null), actualResult);
    verify(circulationSettingsClient).getMany(any(), any());
  }

}
