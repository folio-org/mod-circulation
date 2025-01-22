package org.folio.circulation.infrastructure.storage;

import static org.folio.circulation.domain.MultipleRecords.from;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;

import java.time.ZoneId;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.folio.circulation.domain.Configuration;
import org.folio.circulation.domain.ConfigurationService;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.anonymization.config.LoanAnonymizationConfiguration;
import org.folio.circulation.domain.configuration.PrintHoldRequestsConfiguration;
import org.folio.circulation.domain.configuration.TlrSettingsConfiguration;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.GetManyRecordsClient;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class ConfigurationRepository {
  private static final String CONFIGS_KEY = "configs";
  private static final String MODULE_NAME_KEY = "module";
  private static final String CONFIG_NAME_KEY = "configName";

  private static final PageLimit DEFAULT_PAGE_LIMIT = PageLimit.one();

  private final GetManyRecordsClient configurationClient;

  public ConfigurationRepository(Clients clients) {
    configurationClient = clients.configurationStorageClient();
  }

  public CompletableFuture<Result<PageLimit>> lookupSchedulerNoticesProcessingLimit() {
    Result<CqlQuery> cqlQueryResult = defineModuleNameAndConfigNameFilter(
      "NOTIFICATION_SCHEDULER", "noticesLimit");

    return lookupConfigurations(cqlQueryResult, applySearchSchedulerNoticesLimit())
      .thenApply(result -> result.map(PageLimit::limit));
  }

  public CompletableFuture<Result<Integer>> lookupSessionTimeout() {
    Result<CqlQuery> otherSettingsQuery = defineModuleNameAndConfigNameFilter(
      "CHECKOUT", "other_settings");

    return lookupConfigurations(otherSettingsQuery, applySessionTimeout());
  }

  public CompletableFuture<Result<TlrSettingsConfiguration>> lookupTlrSettings() {
    log.info("lookupTlrSettings:: fetching TLR configuration");
    Result<CqlQuery> queryResult = defineModuleNameAndConfigNameFilter(
      "SETTINGS", "TLR");

    return findAndMapFirstConfiguration(queryResult, TlrSettingsConfiguration::from);
  }

  public CompletableFuture<Result<PrintHoldRequestsConfiguration>> lookupPrintHoldRequestsEnabled() {
    log.info("lookupPrintHoldRequestsEnabled:: fetching PrintHoldRequest configuration");

    return findAndMapFirstConfiguration(defineModuleNameAndConfigNameFilter(
      "SETTINGS", "PRINT_HOLD_REQUESTS"), PrintHoldRequestsConfiguration::from);
  }

  /**
   * Gets loan history tenant configuration - settings for loan anonymization
   *
   */
  public CompletableFuture<Result<LoanAnonymizationConfiguration>> loanHistoryConfiguration() {
    return defineModuleNameAndConfigNameFilter("LOAN_HISTORY", "loan_history")
      .after(query -> configurationClient.getMany(query, DEFAULT_PAGE_LIMIT))
      .thenApply(result -> result.next(response ->
        MultipleRecords.from(response, Configuration::new, CONFIGS_KEY)))
      .thenApply(r -> r.next(r1 -> r.map(MultipleRecords::getRecords)))
      .thenApply(r -> r.map(ConfigurationRepository::getFirstConfiguration));
  }

  private static LoanAnonymizationConfiguration getFirstConfiguration(
    Collection<Configuration> configurations) {

    final JsonObject period = configurations.stream()
        .map(Configuration::getValue)
        .findFirst()
        .map(JsonObject::new)
        .orElse(new JsonObject());

    return LoanAnonymizationConfiguration.from(period);
  }

  public CompletableFuture<Result<ZoneId>> findTimeZoneConfiguration() {
    Result<CqlQuery> cqlQueryResult = defineModuleNameAndConfigNameFilter(
      "ORG", "localeSettings");

    return lookupConfigurations(cqlQueryResult, applySearchDateTimeZone());
  }

  private <T> CompletableFuture<Result<T>> lookupConfigurations(
    Result<CqlQuery> cqlQueryResult,
    Function<MultipleRecords<Configuration>, T> searchStrategy) {

    return cqlQueryResult
      .after(query -> configurationClient.getMany(query, DEFAULT_PAGE_LIMIT))
      .thenApply(result -> result.next(response ->
        from(response, Configuration::new, CONFIGS_KEY)))
      .thenApply(result -> result.map(searchStrategy));
  }

  private Result<CqlQuery> defineModuleNameAndConfigNameFilter(String moduleName,
    String configName) {

    final Result<CqlQuery> moduleQuery = exactMatch(MODULE_NAME_KEY, moduleName);
    final Result<CqlQuery> configNameQuery = exactMatch(CONFIG_NAME_KEY, configName);

    return moduleQuery.combine(configNameQuery, CqlQuery::and);
  }

  private Function<MultipleRecords<Configuration>, ZoneId> applySearchDateTimeZone() {
    return configurations -> new ConfigurationService()
      .findDateTimeZone(configurations.getRecords());
  }

  private Function<MultipleRecords<Configuration>, Integer> applySearchSchedulerNoticesLimit() {
    return configurations -> new ConfigurationService()
      .findSchedulerNoticesLimit(configurations.getRecords());
  }

  private Function<MultipleRecords<Configuration>, Integer> applySessionTimeout() {
    return configurations -> new ConfigurationService()
      .findSessionTimeout(configurations.getRecords());
  }

  /**
   * Find first configuration and maps it to an object with a provided mapper
   */
  private <T> CompletableFuture<Result<T>> findAndMapFirstConfiguration(
    Result<CqlQuery> cqlQueryResult, Function<JsonObject, T> mapper) {

    return cqlQueryResult
      .after(query -> configurationClient.getMany(query, DEFAULT_PAGE_LIMIT))
      .thenApply(result -> result.next(r -> from(r, Configuration::new, CONFIGS_KEY)))
      .thenApply(result -> result.map(this::findFirstConfigurationAsJsonObject))
      .thenApply(result -> result.map(mapper));
  }

  private JsonObject findFirstConfigurationAsJsonObject(
    MultipleRecords<Configuration> configurations) {

    return configurations.getRecords().stream()
      .findFirst()
      .map(Configuration::getValue)
      .map(JsonObject::new)
      .orElse(new JsonObject());
  }
}
