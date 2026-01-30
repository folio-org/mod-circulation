package org.folio.circulation.infrastructure.storage;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.anonymization.RequestAnonymizationSettings;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class RequestAnonymizationSettingsRepository {

  public static final String REQUEST_ANONYMIZATION_SETTING_NAME = "requestAnonymization";

  private static final String SETTINGS_ARRAY_FIELD = "circulationSettings";

  private final CollectionResourceClient circulationSettingsClient;

  public RequestAnonymizationSettingsRepository(Clients clients) {
    this.circulationSettingsClient = clients.circulationSettingsStorageClient();
  }

  public RequestAnonymizationSettingsRepository(CollectionResourceClient circulationSettingsClient) {
    this.circulationSettingsClient = circulationSettingsClient;
  }

  public CompletableFuture <Result<RequestAnonymizationSettings>> getSettings() {
    final CqlQuery query = CqlQuery.exactMatch("name", REQUEST_ANONYMIZATION_SETTING_NAME).value();
    return circulationSettingsClient.getMany(query,null)
      .thenApply(result->result.map(this::toSettings));
  }

  private RequestAnonymizationSettings toSettings(Response response) {
    if (response == null) {
      return RequestAnonymizationSettings.defaultSettings();
    }

    final JsonObject body = response.getJson();
    if (body == null) {
      return RequestAnonymizationSettings.defaultSettings();
    }

    final JsonArray settings = body.getJsonArray(SETTINGS_ARRAY_FIELD);
    if (settings == null || settings.isEmpty()) {
      return RequestAnonymizationSettings.defaultSettings();
    }

    final JsonObject firstRow = settings.getJsonObject(0);
    final JsonObject value = firstRow.getJsonObject("value");

    return RequestAnonymizationSettings.from(value);
  }
}
