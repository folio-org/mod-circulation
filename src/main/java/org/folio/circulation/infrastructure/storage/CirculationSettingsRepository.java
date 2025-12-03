package org.folio.circulation.infrastructure.storage;

import static org.folio.circulation.support.http.ResponseMapping.forwardOnFailure;
import static org.folio.circulation.support.http.ResponseMapping.mapUsingJson;
import static org.folio.circulation.support.http.client.PageLimit.one;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.ResultBinding.flatMapResult;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.lang.invoke.MethodHandles;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.CirculationSetting;
import org.folio.circulation.domain.Configuration;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.anonymization.config.LoanAnonymizationConfiguration;
import org.folio.circulation.domain.configuration.PrintHoldRequestsConfiguration;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.RecordNotFoundFailure;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.http.client.ResponseInterpreter;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;

public class CirculationSettingsRepository {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  public static final String RECORDS_PROPERTY_NAME = "circulationSettings";

  // unified TLR feature settings migrated from single-tenant mod-configuration
  private static final String SETTING_NAME_TLR = "TLR";
  // TLR notice templates settings migrated from multi-tenant mod-settings
  private static final String SETTING_NAME_REGULAR_TLR = "regularTlr";
  // TLR settings migrated from multi-tenant mod-settings
  private static final String SETTING_NAME_GENERAL_TLR = "generalTlr";
  private static final String SETTING_NAME_PRINT_HOLD_REQUESTS = "PRINT_HOLD_REQUESTS";
  private static final String SETTING_NAME_NOTICES_LIMIT = "noticesLimit";
  private static final String SETTING_NAME_OTHER_SETTINGS = "other_settings";
  private static final String SETTING_NAME_LOAN_HISTORY = "loan_history";

  private static final int DEFAULT_SCHEDULED_NOTICES_PROCESSING_LIMIT = 100;
  private static final int DEFAULT_CHECKOUT_TIMEOUT_DURATION_IN_MINUTES = 3;
  private static final String CHECKOUT_TIMEOUT_DURATION_KEY = "checkoutTimeoutDuration";
  private static final String CHECKOUT_TIMEOUT_KEY = "checkoutTimeout";

  private final CollectionResourceClient circulationSettingsStorageClient;

  public CirculationSettingsRepository(Clients clients) {
    circulationSettingsStorageClient = clients.circulationSettingsStorageClient();
  }

  public CompletableFuture<Result<CirculationSetting>> getById(String id) {
    log.debug("getById:: parameters id: {}", id);

    return FetchSingleRecord.<CirculationSetting>forRecord(RECORDS_PROPERTY_NAME)
      .using(circulationSettingsStorageClient)
      .mapTo(CirculationSetting::from)
      .whenNotFound(failed(new RecordNotFoundFailure(RECORDS_PROPERTY_NAME, id)))
      .fetch(id);
  }

  private CompletableFuture<Result<Optional<CirculationSetting>>> findByName(String name) {
    log.debug("getByName:: name: {}", name);

    return CqlQuery.exactMatch("name", name)
      .after(query -> circulationSettingsStorageClient.getMany(query, one()))
      .thenApply(flatMapResult(r -> MultipleRecords.from(r, CirculationSetting::from, RECORDS_PROPERTY_NAME)
        .map(MultipleRecords::firstOrNull)
        .map(Optional::ofNullable)));
  }

  public CompletableFuture<Result<MultipleRecords<CirculationSetting>>> findBy(String query) {
    return circulationSettingsStorageClient.getManyWithRawQueryStringParameters(query)
      .thenApply(flatMapResult(response ->
        MultipleRecords.from(response, CirculationSetting::from, RECORDS_PROPERTY_NAME)));
  }

  public CompletableFuture<Result<CirculationSetting>> create(
    CirculationSetting circulationSetting) {

    log.debug("create:: parameters circulationSetting: {}", circulationSetting);

    final var storageCirculationSetting = circulationSetting.getRepresentation();

    return circulationSettingsStorageClient.post(storageCirculationSetting)
      .thenApply(interpreter()::flatMap);
  }

  public CompletableFuture<Result<CirculationSetting>> update(
    CirculationSetting circulationSetting) {

    log.debug("update:: parameters circulationSetting: {}", circulationSetting);

    final var storageCirculationSetting = circulationSetting.getRepresentation();

    return circulationSettingsStorageClient.put(circulationSetting.getId(), storageCirculationSetting)
      .thenApply(interpreter()::flatMap);
  }

  public CompletableFuture<Result<PageLimit>> getScheduledNoticesProcessingLimit() {
    return findByName(SETTING_NAME_NOTICES_LIMIT)
      .thenApply(r -> r.map(setting -> setting
        .map(CirculationSetting::getValue)
        .map(json -> json.getString("value"))
        .filter(StringUtils::isNumeric)
        .map(Integer::valueOf)
        .map(PageLimit::limit)
        .orElseGet(() -> PageLimit.limit(DEFAULT_SCHEDULED_NOTICES_PROCESSING_LIMIT))));
  }

  public CompletableFuture<Result<PrintHoldRequestsConfiguration>> getPrintHoldRequestsEnabled() {
    return findByName(SETTING_NAME_PRINT_HOLD_REQUESTS)
      .thenApply(mapResult(setting -> setting
        .map(CirculationSetting::getValue)
        .map(PrintHoldRequestsConfiguration::from)
        .orElseGet(() -> new PrintHoldRequestsConfiguration(false))));
  }

  public CompletableFuture<Result<LoanAnonymizationConfiguration>> getLoanAnonymizationSettings() {
    return findByName(SETTING_NAME_LOAN_HISTORY)
      .thenApply(mapResult(setting -> setting
        .map(CirculationSetting::getValue)
        .map(LoanAnonymizationConfiguration::from)
        .orElseGet(() -> LoanAnonymizationConfiguration.from(new JsonObject()))));
  }

  public CompletableFuture<Result<Integer>> getCheckOutSessionTimeout() {
    return findByName(SETTING_NAME_OTHER_SETTINGS)
      .thenApply(mapResult(setting -> setting
        .map(CirculationSetting::getValue)
        .map(CirculationSettingsRepository::extractCheckOutSessionTimeout)
        .orElse(DEFAULT_CHECKOUT_TIMEOUT_DURATION_IN_MINUTES)));
  }

  private static Integer extractCheckOutSessionTimeout(JsonObject loanHistorySettingValue) {
    if (isConfigurationEmptyOrUnavailable(loanHistorySettingValue)) {
      return DEFAULT_CHECKOUT_TIMEOUT_DURATION_IN_MINUTES;
    }
    try {
      return loanHistorySettingValue.getInteger(CHECKOUT_TIMEOUT_DURATION_KEY,
        DEFAULT_CHECKOUT_TIMEOUT_DURATION_IN_MINUTES);
    } catch (Exception e) {
      log.warn("extractCheckOutSessionTimeout:: using default value: {}",
        DEFAULT_CHECKOUT_TIMEOUT_DURATION_IN_MINUTES, e);
      return DEFAULT_CHECKOUT_TIMEOUT_DURATION_IN_MINUTES;
    }
  }

  private static boolean isConfigurationEmptyOrUnavailable(JsonObject configJson) {
    return configJson.isEmpty() || !configJson.getBoolean(CHECKOUT_TIMEOUT_KEY, false);
  }

  private ResponseInterpreter<CirculationSetting> interpreter() {
    return new ResponseInterpreter<CirculationSetting>()
      .flatMapOn(201, mapUsingJson(CirculationSetting::from))
      .otherwise(forwardOnFailure());
  }
}
