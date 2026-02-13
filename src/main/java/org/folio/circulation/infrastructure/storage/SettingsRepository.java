package org.folio.circulation.infrastructure.storage;

import static java.util.function.Function.identity;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatchAny;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Configuration;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.configuration.CheckoutLockConfiguration;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.GetManyRecordsClient;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;

public class SettingsRepository {
  private static final ZoneId DEFAULT_DATE_TIME_ZONE = ZoneOffset.UTC;
  private static final String TIMEZONE_KEY = "timezone";

  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private final GetManyRecordsClient settingsClient;

  public SettingsRepository(Clients clients) {
    settingsClient = clients.settingsStorageClient();
  }

  public CompletableFuture<Result<CheckoutLockConfiguration>> lookUpCheckOutLockSettings() {
    log.debug("lookUpCheckOutLockSettings:: fetching checkout lock settings");
    try {
      return fetchSettings("mod-circulation", "checkoutLockFeature")
        .thenApply(r -> r.map(records -> records.mapRecords(Configuration::new)))
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

  public CompletableFuture<Result<ZoneId>> lookupTimeZoneSettings() {
    log.info("lookupTimeZoneSettings:: fetching timezone settings from /locale API");
    return CqlQuery.noQuery()
      .after(query -> settingsClient.getMany(query, PageLimit.noLimit()))
      .thenApply(r -> r.next(response -> MultipleRecords.from(response, identity(), "items")))
      .thenApply(r -> r.map(r1 -> r1.getRecords().stream()
        .filter(record -> record.containsKey(TIMEZONE_KEY))  // Filter for records with timezone field
        .findFirst()
        .map(this::applyTimeZone)
        .orElse(DEFAULT_DATE_TIME_ZONE)))
      .thenApply(r -> r.mapFailure(failure -> {
        log.warn("lookupTimeZoneSettings:: Error while fetching timezone settings {}", failure);
        return succeeded(DEFAULT_DATE_TIME_ZONE);
      }));
  }

  private ZoneId applyTimeZone(JsonObject localeSettings) {
    // New /locale API returns timezone directly in the response
    return Optional.ofNullable(getProperty(localeSettings, TIMEZONE_KEY))
      .filter(StringUtils::isNotBlank)
      .map(ZoneId::of)
      .orElse(DEFAULT_DATE_TIME_ZONE);
  }

  private CompletableFuture<Result<MultipleRecords<JsonObject>>> fetchSettings(String scope, String key) {
    return fetchSettings(scope, List.of(key));
  }

  private CompletableFuture<Result<MultipleRecords<JsonObject>>> fetchSettings(String scope,
    Collection<String> keys) {

    return exactMatch("scope", scope)
      .combine(exactMatchAny("key", keys), CqlQuery::and)
      .after(query -> settingsClient.getMany(query, PageLimit.noLimit()))
      .thenApply(r -> r.next(response -> MultipleRecords.from(response, identity(), "items")));
  }

}
