package org.folio.circulation.services;

import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.CirculationSetting;
import org.folio.circulation.domain.anonymization.config.LoanAnonymizationConfiguration;
import org.folio.circulation.domain.configuration.PrintHoldRequestsConfiguration;
import org.folio.circulation.domain.configuration.TlrSettingsConfiguration;
import org.folio.circulation.infrastructure.storage.CirculationSettingsRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class CirculationSettingsService {

  // unified TLR feature settings migrated from single-tenant mod-configuration
  private static final String SETTING_NAME_TLR = "TLR";
  // TLR notice templates settings migrated from multi-tenant mod-settings
  private static final String SETTING_NAME_REGULAR_TLR = "regularTlr";
  // TLR settings migrated from multi-tenant mod-settings
  private static final String SETTING_NAME_GENERAL_TLR = "generalTlr";
  public static final List<String> ALL_TLR_SETTINGS_NAMES =
    List.of(SETTING_NAME_TLR, SETTING_NAME_GENERAL_TLR, SETTING_NAME_REGULAR_TLR);
  private static final String SETTING_NAME_PRINT_HOLD_REQUESTS = "PRINT_HOLD_REQUESTS";
  private static final String SETTING_NAME_NOTICES_LIMIT = "noticesLimit";
  private static final String SETTING_NAME_OTHER_SETTINGS = "other_settings";
  private static final String SETTING_NAME_LOAN_HISTORY = "loan_history";

  private static final int DEFAULT_SCHEDULED_NOTICES_PROCESSING_LIMIT = 100;
  private static final int DEFAULT_CHECKOUT_SESSION_TIMEOUT_MINUTES = 3;
  private static final String CHECKOUT_TIMEOUT_DURATION_KEY = "checkoutTimeoutDuration";
  private static final String CHECKOUT_TIMEOUT_KEY = "checkoutTimeout";

  private final CirculationSettingsRepository circulationSettingsRepository;

  public CirculationSettingsService(Clients clients) {
    this.circulationSettingsRepository = new CirculationSettingsRepository(clients);
  }

  public CompletableFuture<Result<PageLimit>> getScheduledNoticesProcessingLimit() {
    log.debug("getScheduledNoticesProcessingLimit:: fetching scheduled notices processing limit");
    return getSetting(SETTING_NAME_NOTICES_LIMIT,
      CirculationSettingsService::extractScheduledNoticesProcessingLimit,
      () -> PageLimit.limit(DEFAULT_SCHEDULED_NOTICES_PROCESSING_LIMIT));
  }

  private static PageLimit extractScheduledNoticesProcessingLimit(JsonObject settingValue) {
    return PageLimit.limit(
      Optional.ofNullable(settingValue)
        .map(value -> value.getString("value"))
        .filter(StringUtils::isNumeric)
        .map(Integer::parseInt)
        .orElse(DEFAULT_SCHEDULED_NOTICES_PROCESSING_LIMIT)
    );
  }

  public CompletableFuture<Result<PrintHoldRequestsConfiguration>> getPrintHoldRequestsEnabled() {
    log.debug("getPrintHoldRequestsEnabled:: fetching print hold requests setting");
    return getSetting(SETTING_NAME_PRINT_HOLD_REQUESTS,
      PrintHoldRequestsConfiguration::from,
      () -> new PrintHoldRequestsConfiguration(false));
  }

  public CompletableFuture<Result<LoanAnonymizationConfiguration>> getLoanAnonymizationSettings() {
    log.debug("getLoanAnonymizationSettings:: fetching loan anonymization settings");
    return getSetting(SETTING_NAME_LOAN_HISTORY,
      LoanAnonymizationConfiguration::from,
      () -> LoanAnonymizationConfiguration.from(new JsonObject()));
  }

  public CompletableFuture<Result<Integer>> getCheckOutSessionTimeout() {
    log.debug("getCheckOutSessionTimeout:: fetching checkout session timeout setting");
    return getSetting(SETTING_NAME_OTHER_SETTINGS,
      CirculationSettingsService::extractCheckOutSessionTimeout,
      () -> DEFAULT_CHECKOUT_SESSION_TIMEOUT_MINUTES);
  }

  private static Integer extractCheckOutSessionTimeout(JsonObject value) {
    return value.isEmpty() || !value.getBoolean(CHECKOUT_TIMEOUT_KEY, false)
      ? DEFAULT_CHECKOUT_SESSION_TIMEOUT_MINUTES
      : value.getInteger(CHECKOUT_TIMEOUT_DURATION_KEY, DEFAULT_CHECKOUT_SESSION_TIMEOUT_MINUTES);
  }

  public CompletableFuture<Result<TlrSettingsConfiguration>> getTlrSettings() {
    log.debug("getTlrSettings:: fetching TLR settings");
    return circulationSettingsRepository.findByNames(ALL_TLR_SETTINGS_NAMES)
      .thenApply(mapResult(CirculationSettingsService::buildTlrSettings));
  }

  private static TlrSettingsConfiguration buildTlrSettings(Collection<CirculationSetting> tlrSettings) {
    if (tlrSettings.isEmpty()) {
      log.warn("buildTlrSettings:: failed to find TLR settings, falling back to default settings");
      return TlrSettingsConfiguration.defaultSettings();
    }

    log.debug("buildTlrSettings:: settings: {}", tlrSettings);
    JsonObject mergedSettings = tlrSettings.stream()
      .filter(s -> List.of(SETTING_NAME_GENERAL_TLR, SETTING_NAME_REGULAR_TLR).contains(s.getName()))
      .map(CirculationSetting::getValue)
      .reduce(new JsonObject(), JsonObject::mergeIn);

    if (!mergedSettings.isEmpty()) {
      log.debug("buildTlrSettings:: returning merged TLR settings");
      return TlrSettingsConfiguration.from(mergedSettings);
    }

    return tlrSettings.stream()
      .filter(setting -> SETTING_NAME_TLR.equals(setting.getName()))
      .findFirst()
      .map(CirculationSetting::getValue)
      .map(TlrSettingsConfiguration::from)
      .orElseGet(TlrSettingsConfiguration::defaultSettings);
  }

  private <T> CompletableFuture<Result<T>> getSetting(String name,
    Function<JsonObject, T> valueMapper, Supplier<T> defaultValueSupplier) {

    log.debug("getSetting:: parameters name: {}", name);
    return circulationSettingsRepository.findByName(name)
      .thenApply(mapResult(setting -> setting.map(CirculationSetting::getValue)))
      .thenApply(mapResult(value -> value.map(valueMapper).orElseGet(defaultValueSupplier)));
  }

}
