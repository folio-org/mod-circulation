package org.folio.circulation.infrastructure.storage;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
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
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static java.util.function.Function.identity;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatchAny;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;

public class SettingsRepository {
  private static final ZoneId DEFAULT_DATE_TIME_ZONE = ZoneOffset.UTC;
  private static final String TIMEZONE_KEY = "timezone";
  private static final String TIMEZONE_SETTINGS_SCOPE = "stripes-core.prefs.manage";
  private static final String TIMEZONE_SETTINGS_KEY = "tenantLocaleSettings";

  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private final GetManyRecordsClient settingsClient;
  private final ConfigurationRepository configurationRepository;

  public SettingsRepository(Clients clients) {
    settingsClient = clients.settingsStorageClient();
    configurationRepository = new ConfigurationRepository(clients);
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

  public CompletableFuture<Result<TlrSettingsConfiguration>> lookupTlrSettings() {
    log.info("lookupTlrSettings:: fetching TLR settings");
    return fetchSettings("circulation", List.of("generalTlr", "regularTlr"))
      .thenApply(r -> r.map(SettingsRepository::extractAndMergeValues))
      .thenCompose(r -> r.after(this::buildTlrSettings));
  }

  public CompletableFuture<Result<ZoneId>> lookupTimeZoneSettings() {
    log.info("lookupTimeZoneSettings:: fetching timezone settings");
    return fetchSettings(TIMEZONE_SETTINGS_SCOPE, TIMEZONE_SETTINGS_KEY)
      .thenApply(r -> r.map(records -> records.mapRecords(Configuration::new)))
      .thenApply(r -> r.map(r1 -> r1.getRecords().stream().findFirst()
        .map(this::applyTimeZone)
        .orElse(DEFAULT_DATE_TIME_ZONE)))
      .thenApply(r -> r.mapFailure(failure -> {
        log.warn("lookupTimeZoneSettings:: Error while fetching timezone settings {}", failure);
        return succeeded(DEFAULT_DATE_TIME_ZONE);
      }));
  }

  private ZoneId applyTimeZone(Configuration config) {
    String value = config.getValue();
    return StringUtils.isBlank(value)
      ? DEFAULT_DATE_TIME_ZONE
      : parseDateTimeZone(value);
  }

  private ZoneId parseDateTimeZone(String value) {
    String timezone = new JsonObject(value).getString(TIMEZONE_KEY);
    return StringUtils.isBlank(timezone)
      ? DEFAULT_DATE_TIME_ZONE
      : ZoneId.of(timezone);
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

  private static JsonObject extractAndMergeValues(MultipleRecords<JsonObject> entries) {
    return entries.getRecords()
      .stream()
      .map(rec -> rec.getJsonObject("value"))
      .reduce(new JsonObject(), JsonObject::mergeIn);
  }

  private CompletableFuture<Result<TlrSettingsConfiguration>> buildTlrSettings(JsonObject tlrSettings) {
    if (tlrSettings.isEmpty()) {
      log.info("getTlrSettings:: failed to find TLR settings, falling back to legacy configuration");
      return configurationRepository.lookupTlrSettings();
    }

    return ofAsync(TlrSettingsConfiguration.from(tlrSettings));
  }
}
