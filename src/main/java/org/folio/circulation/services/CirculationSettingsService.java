package org.folio.circulation.services;

import static org.folio.circulation.support.http.client.PageLimit.limit;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
  private static final String SETTING_NAME_PRINT_HOLD_REQUESTS = "PRINT_HOLD_REQUESTS";
  private static final String SETTING_NAME_NOTICES_LIMIT = "noticesLimit";
  private static final String SETTING_NAME_OTHER_SETTINGS = "other_settings";
  private static final String SETTING_NAME_LOAN_HISTORY = "loan_history";

  private static final int DEFAULT_SCHEDULED_NOTICES_PROCESSING_LIMIT = 100;
  private static final int DEFAULT_CHECKOUT_TIMEOUT_DURATION_IN_MINUTES = 3;
  private static final String CHECKOUT_TIMEOUT_DURATION_KEY = "checkoutTimeoutDuration";
  private static final String CHECKOUT_TIMEOUT_KEY = "checkoutTimeout";

  private final CirculationSettingsRepository circulationSettingsRepository;

  public CirculationSettingsService(Clients clients) {
    this.circulationSettingsRepository = new CirculationSettingsRepository(clients);
  }

  public CompletableFuture<Result<PageLimit>> getScheduledNoticesProcessingLimit() {
    return circulationSettingsRepository.findByName(SETTING_NAME_NOTICES_LIMIT)
      .thenApply(r -> r.map(setting -> setting
        .map(CirculationSetting::getValue)
        .map(json -> json.getString("value"))
        .filter(StringUtils::isNumeric)
        .map(Integer::valueOf)
        .map(PageLimit::limit)
        .orElseGet(() -> limit(DEFAULT_SCHEDULED_NOTICES_PROCESSING_LIMIT))));
  }

  public CompletableFuture<Result<PrintHoldRequestsConfiguration>> getPrintHoldRequestsEnabled() {
    return circulationSettingsRepository.findByName(SETTING_NAME_PRINT_HOLD_REQUESTS)
      .thenApply(mapResult(setting -> setting
        .map(CirculationSetting::getValue)
        .map(PrintHoldRequestsConfiguration::from)
        .orElseGet(() -> new PrintHoldRequestsConfiguration(false))));
  }

  public CompletableFuture<Result<LoanAnonymizationConfiguration>> getLoanAnonymizationSettings() {
    return circulationSettingsRepository.findByName(SETTING_NAME_LOAN_HISTORY)
      .thenApply(mapResult(setting -> setting
        .map(CirculationSetting::getValue)
        .map(LoanAnonymizationConfiguration::from)
        .orElseGet(() -> LoanAnonymizationConfiguration.from(new JsonObject()))));
  }

  public CompletableFuture<Result<Integer>> getCheckOutSessionTimeout() {
    return circulationSettingsRepository.findByName(SETTING_NAME_OTHER_SETTINGS)
      .thenApply(mapResult(setting -> setting
        .map(CirculationSetting::getValue)
        .map(CirculationSettingsService::extractCheckOutSessionTimeout)
        .orElse(DEFAULT_CHECKOUT_TIMEOUT_DURATION_IN_MINUTES)));
  }

  private static Integer extractCheckOutSessionTimeout(JsonObject value) {
    if (value.isEmpty() || !value.getBoolean(CHECKOUT_TIMEOUT_KEY, false)) {
      return DEFAULT_CHECKOUT_TIMEOUT_DURATION_IN_MINUTES;
    }
    try {
      return value.getInteger(CHECKOUT_TIMEOUT_DURATION_KEY,
        DEFAULT_CHECKOUT_TIMEOUT_DURATION_IN_MINUTES);
    } catch (Exception e) {
      log.warn("extractCheckOutSessionTimeout:: failed to extract timeout value, using default value: {}",
        DEFAULT_CHECKOUT_TIMEOUT_DURATION_IN_MINUTES, e);
      return DEFAULT_CHECKOUT_TIMEOUT_DURATION_IN_MINUTES;
    }
  }

  public CompletableFuture<Result<TlrSettingsConfiguration>> getTlrSettings() {
    return circulationSettingsRepository.findByNames(List.of(SETTING_NAME_TLR, SETTING_NAME_GENERAL_TLR, SETTING_NAME_REGULAR_TLR))
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
}
