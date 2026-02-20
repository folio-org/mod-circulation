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
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.GetManyRecordsClient;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;

public class SettingsRepository {
  private static final ZoneId DEFAULT_DATE_TIME_ZONE = ZoneOffset.UTC;
  private static final String TIMEZONE_KEY = "timezone";

  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private final GetManyRecordsClient settingsClient;
  private final CollectionResourceClient localeClient;

  public SettingsRepository(Clients clients) {
    settingsClient = clients.settingsStorageClient();
    localeClient = clients.localeClient();
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
    log.info("lookupTimeZoneSettings:: fetching timezone settings from /locale endpoint");

    return localeClient.get()
      .thenApply(r -> r.next(this::extractTimeZoneFromLocaleResponse))
      .thenApply(r -> r.mapFailure(failure -> {
        log.warn("lookupTimeZoneSettings:: Failed to fetch timezone settings, using default UTC. Failure: {}",
          failure);
        return succeeded(DEFAULT_DATE_TIME_ZONE);
      }));
  }

  private Result<ZoneId> extractTimeZoneFromLocaleResponse(Response localeResponse) {
    log.info("extractTimeZoneFromLocaleResponse:: status code: {}", localeResponse.getStatusCode());

    if (localeResponse.getStatusCode() != 200) {
      log.warn("extractTimeZoneFromLocaleResponse:: /locale endpoint returned status {}, using default UTC",
        localeResponse.getStatusCode());
      return succeeded(DEFAULT_DATE_TIME_ZONE);
    }

    String timezoneValue = getProperty(localeResponse.getJson(), TIMEZONE_KEY);

    log.info("extractTimeZoneFromLocaleResponse:: extracted timezone: {}", timezoneValue);

    ZoneId timezone = Optional.ofNullable(timezoneValue)
      .filter(StringUtils::isNotBlank)
      .map(ZoneId::of)
      .orElse(DEFAULT_DATE_TIME_ZONE);

    log.info("extractTimeZoneFromLocaleResponse:: using ZoneId: {}", timezone);

    return succeeded(timezone);
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
