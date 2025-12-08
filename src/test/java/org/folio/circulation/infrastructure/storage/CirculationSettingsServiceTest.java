package org.folio.circulation.infrastructure.storage;

import static org.folio.circulation.support.results.Result.ofAsync;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.folio.circulation.domain.configuration.TlrSettingsConfiguration;
import org.folio.circulation.services.CirculationSettingsService;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.client.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.tomakehurst.wiremock.http.MimeType;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.SneakyThrows;

@ExtendWith(MockitoExtension.class)
class CirculationSettingsServiceTest {

  @Mock
  private Clients clients;
  @Mock
  private CollectionResourceClient circulationSettingsClient;
  private CirculationSettingsService circulationSettingsService;

  @BeforeEach
  void beforeEach() {
    when(clients.circulationSettingsStorageClient())
      .thenReturn(circulationSettingsClient);
    circulationSettingsService = new CirculationSettingsService(clients);
  }

  @Test
  void fetchTlrSettings() {
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

    TlrSettingsConfiguration expected = new TlrSettingsConfiguration(true, false, true,
      UUID.fromString("51958757-df1d-4c71-84d3-820575d73f81"),
      UUID.fromString("51958757-df1d-4c71-84d3-820575d73f82"),
      UUID.fromString("51958757-df1d-4c71-84d3-820575d73f83"));

    verifySettings(mockSettingsResponse, expected);
  }

  @Test
  void fallBackToLegacySettingsWhenTlrSettingsAreNotFound() {
    JsonObject mockSettingsResponse = new JsonObject()
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

    TlrSettingsConfiguration expected = new TlrSettingsConfiguration(true, true, true, null, null, null);
    verifySettings(mockSettingsResponse, expected);
  }

  @Test
  void fallBackToDefaultSettingsWhenNoTlrSettingsAreFound() {
    JsonObject mockSettingsResponse = new JsonObject()
      .put("circulationSettings", new JsonArray())
      .put("totalRecords", 0);

    TlrSettingsConfiguration expected = new TlrSettingsConfiguration(false, false, false, null, null, null);
    verifySettings(mockSettingsResponse, expected);
  }

  private void verifySettings(JsonObject mockSettingsResponse, TlrSettingsConfiguration expectedResult) {
    mockGetCirculationSettingsResponse(mockSettingsResponse);
    assertEquals(expectedResult, getTlrSettings());
  }

  private void mockGetCirculationSettingsResponse(JsonObject responseBody) {
    when(circulationSettingsClient.getMany(any(), any()))
      .thenReturn(ofAsync(new Response(200, responseBody.encode(), MimeType.JSON.toString())));
  }

  @SneakyThrows
  private TlrSettingsConfiguration getTlrSettings() {
    return circulationSettingsService.getTlrSettings()
      .get(30, TimeUnit.SECONDS)
      .value();
  }
}
