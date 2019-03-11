package org.folio.circulation.domain;

import static org.folio.circulation.domain.MultipleRecords.from;
import static org.folio.circulation.support.CqlHelper.encodeQuery;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.HttpResult;
import org.joda.time.DateTimeZone;

public class ConfigurationRepository {

  private static final String QUERY_PARAMETERS = "module==%s and configName==%s";
  private static final String MODULE_VAL = "ORG";
  private static final String LOCALE_SETTINGS_VAL = "localeSettings";
  private static final String RECORDS_NAME = "configs";

  private final CollectionResourceClient configurationClient;

  public ConfigurationRepository(Clients clients) {
    configurationClient = clients.configurationStorageClient();
  }

  public CompletableFuture<HttpResult<LoanAndRelatedRecords>> lookupTimeZone(LoanAndRelatedRecords relatedRecords) {
    return findTimeZoneConfiguration()
      .thenApply(result -> result.map(relatedRecords::withTimeZone));
  }

  private CompletableFuture<HttpResult<DateTimeZone>> findTimeZoneConfiguration() {

    final ConfigurationService configurationService = new ConfigurationService();
    String unEncodedQuery = String.format(QUERY_PARAMETERS, MODULE_VAL, LOCALE_SETTINGS_VAL);

    return encodeQuery(unEncodedQuery)
      .after(query -> findBy(query)
        .thenApply(result -> result.map(configurations ->
          configurationService.findDateTimeZone(configurations.getRecords()))));
  }

  private CompletableFuture<HttpResult<MultipleRecords<TimeZoneConfig>>> findBy(String query) {
    return configurationClient.getMany(query, 1, 0)
      .thenApply(response -> from(response, TimeZoneConfig::new, RECORDS_NAME));
  }

}
