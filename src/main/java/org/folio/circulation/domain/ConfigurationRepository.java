package org.folio.circulation.domain;

import static org.folio.circulation.domain.MultipleRecords.from;
import static org.folio.circulation.support.CqlQuery.exactMatch;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.CqlQuery;
import org.folio.circulation.support.Result;
import org.joda.time.DateTimeZone;

public class ConfigurationRepository {
  private final CollectionResourceClient configurationClient;

  public ConfigurationRepository(Clients clients) {
    configurationClient = clients.configurationStorageClient();
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> lookupTimeZoneForLoanRelatedRecords(
    Result<LoanAndRelatedRecords> relatedRecords) {

    return relatedRecords.combineAfter(r -> findTimeZoneConfiguration(),
      LoanAndRelatedRecords::withTimeZone);
  }

  public CompletableFuture<Result<DateTimeZone>> findTimeZoneConfiguration() {
    final ConfigurationService configurationService = new ConfigurationService();

    final Result<CqlQuery> moduleQuery = exactMatch("module", "ORG");
    final Result<CqlQuery> configNameQuery = exactMatch("configName", "localeSettings");

    return moduleQuery.combine(configNameQuery, CqlQuery::and)
      .after(query -> configurationClient.getMany(query, 1))
      .thenApply(result -> result.next(response ->
        from(response, TimeZoneConfig::new, "configs")))
      .thenApply(result -> result.map(configurations ->
        configurationService.findDateTimeZone(configurations.getRecords())));
  }
}
