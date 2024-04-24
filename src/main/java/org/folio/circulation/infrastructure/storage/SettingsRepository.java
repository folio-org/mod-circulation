package org.folio.circulation.infrastructure.storage;

import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Configuration;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.configuration.CheckoutLockConfiguration;
import org.folio.circulation.domain.configuration.TlrSettingsConfiguration;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.GetManyRecordsClient;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.results.Result;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import static java.util.function.Function.identity;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatchAny;
import static org.folio.circulation.support.results.Result.succeeded;

public class SettingsRepository {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private final GetManyRecordsClient settingsClient;

  public SettingsRepository(Clients clients) {
    settingsClient = clients.settingsStorageClient();
  }

  public CompletableFuture<Result<CheckoutLockConfiguration>> lookUpCheckOutLockSettings() {
    try {
      log.debug("lookUpCheckOutLockSettings:: fetching checkout lock settings");
      final Result<CqlQuery> moduleQuery = exactMatch("scope", "mod-circulation");
      final Result<CqlQuery> configNameQuery = exactMatch("key", "checkoutLockFeature");

      return moduleQuery.combine(configNameQuery, CqlQuery::and)
        .after(cqlQuery -> settingsClient.getMany(cqlQuery, PageLimit.noLimit()))
        .thenApply(result -> result.next(response -> MultipleRecords.from(response, Configuration::new, "items")))
        .thenApply(r -> r.map(r1 -> r1.getRecords().stream().findFirst()
          .map(Configuration::getValue)
          .map(JsonObject::new)
          .orElse(new JsonObject())))
        .thenApply(r -> r.map(CheckoutLockConfiguration::from))
        .thenApply(r -> r.mapFailure(failure -> {
          log.warn("lookUpCheckOutLockSettings:: Error while fetching checkout lock settings {}", failure);
          return succeeded(CheckoutLockConfiguration.from(new JsonObject()));
        }));
    } catch (Exception ex) {
      log.warn("lookUpCheckOutLockSettings:: Unable to retrieve checkoutLockFeature settings ", ex);
      return CompletableFuture.completedFuture(succeeded(CheckoutLockConfiguration.from(new JsonObject())));
    }
  }

  public CompletableFuture<Result<TlrSettingsConfiguration>> lookupTlrSettings() {
    return fetchSettings("circulation", "generalTlr", "regularTlr")
      .thenApply(r -> r.map(SettingsRepository::mergeValues))
      .thenApply(r -> r.map(TlrSettingsConfiguration::from));
  }

  private CompletableFuture<Result<MultipleRecords<JsonObject>>> fetchSettings(String scope, String... keys) {
    return exactMatch("scope", scope)
      .combine(exactMatchAny("key", Arrays.asList(keys)), CqlQuery::and)
      .after(query -> settingsClient.getMany(query, PageLimit.noLimit()))
      .thenApply(r -> r.next(response -> MultipleRecords.from(response, identity(), "items")));
  }

  private static JsonObject mergeValues(MultipleRecords<JsonObject> entries) {
    return entries.getRecords()
      .stream()
      .map(record -> record.getJsonObject("value"))
      .reduce(new JsonObject(), JsonObject::mergeIn);
  }
}
