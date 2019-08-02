package org.folio.circulation.domain;

import static org.folio.circulation.domain.MultipleRecords.from;
import static org.folio.circulation.support.CqlQuery.exactMatch;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.CqlQuery;
import org.folio.circulation.support.Result;
import org.joda.time.DateTimeZone;

public class ConfigurationRepository {

  private static final String CONFIGS_KEY = "configs";
  private static final String MODULE_NAME_KEY = "module";
  private static final String CONFIG_NAME_KEY = "configName";

  private static final int DEFAULT_PAGE_LIMIT = 1;

  private final CollectionResourceClient configurationClient;

  public ConfigurationRepository(Clients clients) {
    configurationClient = clients.configurationStorageClient();
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> lookupTimeZone(
    LoanAndRelatedRecords relatedRecords) {

    return findTimeZoneConfiguration()
      .thenApply(result -> result.map(relatedRecords::withTimeZone));
  }

  public CompletableFuture<Result<Integer>> lookupSchedulerNoticesProcessingLimit() {
    Result<CqlQuery> cqlQueryResult = defineModuleNameAndConfigNameFilter("NOTIFICATION_SCHEDULER", "noticesLimit");
    return lookupConfigurations(cqlQueryResult, applySearchSchedulerNoticesLimit());
  }

  private CompletableFuture<Result<DateTimeZone>> findTimeZoneConfiguration() {
    Result<CqlQuery> cqlQueryResult = defineModuleNameAndConfigNameFilter("ORG", "localeSettings");
    return lookupConfigurations(cqlQueryResult, applySearchDateTimeZone());
  }

  private <T> CompletableFuture<Result<T>> lookupConfigurations(Result<CqlQuery> cqlQueryResult,
                                                                Function<MultipleRecords<Configuration>, T> searchStrategy) {

    return cqlQueryResult
      .after(query -> configurationClient.getMany(query, DEFAULT_PAGE_LIMIT))
      .thenApply(result -> result.next(response -> from(response, Configuration::new, CONFIGS_KEY)))
      .thenApply(result -> result.map(searchStrategy));
  }

  private Result<CqlQuery> defineModuleNameAndConfigNameFilter(String moduleName, String configName) {
    final Result<CqlQuery> moduleQuery = exactMatch(MODULE_NAME_KEY, moduleName);
    final Result<CqlQuery> configNameQuery = exactMatch(CONFIG_NAME_KEY, configName);

    return moduleQuery.combine(configNameQuery, CqlQuery::and);
  }

  private Function<MultipleRecords<Configuration>, DateTimeZone> applySearchDateTimeZone() {
    return configurations -> new ConfigurationService().findDateTimeZone(configurations.getRecords());
  }

  private Function<MultipleRecords<Configuration>, Integer> applySearchSchedulerNoticesLimit() {
    return configurations -> new ConfigurationService().findSchedulerNoticesLimit(configurations.getRecords());
  }
}
