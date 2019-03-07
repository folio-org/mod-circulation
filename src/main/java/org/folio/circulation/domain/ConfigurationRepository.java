package org.folio.circulation.domain;

import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.server.ValidationError;
import org.joda.time.DateTimeZone;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import static org.folio.circulation.support.HttpResult.failed;

public class ConfigurationRepository {

  private static final String ERROR_MESSAGE = "Time zone configuration not found";
  private static final String PATH_QUERY = "?query=(module=ORG%20and%20configName=localeSettings)";
  private static final String RECORD_NAME = "configs";

  private final CollectionResourceClient calendarClient;
  private final ConfigurationService service;

  public ConfigurationRepository(Clients clients) {
    calendarClient = clients.configurationStorageClient();
    service = new ConfigurationService();
  }

  public CompletableFuture<HttpResult<LoanAndRelatedRecords>> lookupTimeZone(LoanAndRelatedRecords relatedRecords) {
    return findTimeZoneConfiguration()
      .thenApply(httpResult ->
        httpResult.map(mapper ->
          relatedRecords.withTimeZone(httpResult.value())));
  }

  private CompletableFuture<HttpResult<DateTimeZone>> findTimeZoneConfiguration() {
    return FetchSingleRecord.<DateTimeZone>forRecord(RECORD_NAME)
      .using(calendarClient)
      .mapTo(service::findDateTimeZone)
      .whenNotFound(failed(new ValidationErrorFailure(
        new ValidationError(ERROR_MESSAGE, Collections.emptyMap()))))
      .fetch(PATH_QUERY);
  }
}
