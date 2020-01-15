package org.folio.circulation.domain;

import static org.folio.circulation.domain.MultipleRecords.from;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.folio.circulation.domain.anonymization.config.LoanAnonymizationConfiguration;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.Limit;
import org.joda.time.DateTimeZone;

import io.vertx.core.json.JsonObject;

public class ConfigurationRepository {
  private static final String CONFIGS_KEY = "configs";
  private static final String MODULE_NAME_KEY = "module";
  private static final String CONFIG_NAME_KEY = "configName";

  private static final Limit DEFAULT_PAGE_LIMIT = Limit.one();

  private final CollectionResourceClient configurationClient;

  public ConfigurationRepository(Clients clients) {
    configurationClient = clients.configurationStorageClient();
  }

  public CompletableFuture<Result<Integer>> lookupSchedulerNoticesProcessingLimit() {
    Result<CqlQuery> cqlQueryResult = defineModuleNameAndConfigNameFilter(
      "NOTIFICATION_SCHEDULER", "noticesLimit");

    return lookupConfigurations(cqlQueryResult, applySearchSchedulerNoticesLimit());
  }

  public CompletableFuture<Result<Integer>> lookupSessionTimeout() {
    Result<CqlQuery> otherSettingsQuery = defineModuleNameAndConfigNameFilter(
      "CHECKOUT", "other_settings");

    return lookupConfigurations(otherSettingsQuery, applySessionTimeout());
  }

  /**
   * Gets loan history tenant configuration - settings for loan anonymization
   *
   */
  public CompletableFuture<Result<LoanAnonymizationConfiguration>> loanHistoryConfiguration() {
    return defineModuleNameAndConfigNameFilter("LOAN_HISTORY", "loan_history")
      .after(query -> configurationClient.getMany(query, DEFAULT_PAGE_LIMIT))
      .thenApply(result -> result.next(response ->
        from(response, Configuration::new, CONFIGS_KEY)))
      .thenApply(r -> r.next(r1 -> r.map(MultipleRecords::getRecords)))
      .thenApply(r -> r.map(this::getFirstConfiguration));
  }

  private LoanAnonymizationConfiguration getFirstConfiguration(
    Collection<Configuration> configurations) {

    final String period = configurations.stream()
      .map(Configuration::getValue)
      .findFirst()
      .orElse("");

    return LoanAnonymizationConfiguration.from(new JsonObject(period));
  }

  public CompletableFuture<Result<DateTimeZone>> findTimeZoneConfiguration() {
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

  private Function<MultipleRecords<Configuration>, DateTimeZone> applySearchDateTimeZone() {
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
}
