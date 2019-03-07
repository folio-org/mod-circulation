package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.server.ValidationError;
import org.joda.time.DateTimeZone;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.folio.circulation.support.HttpResult.failed;

public class ConfigurationRepository {

  private static final DateTimeZone DEFAULT_DATE_TIME_ZONE = DateTimeZone.UTC;
  private static final String ERROR_MESSAGE = "Time zone configuration not found";

  private static final String PATH_QUERY = "?query=(module=ORG%20and%20configName=localeSettings)";
  private static final String RECORD_NAME = "configs";
  private static final String TIMEZONE_KEY = "timezone";

  private final CollectionResourceClient calendarClient;

  public ConfigurationRepository(Clients clients) {
    calendarClient = clients.configurationStorageClient();
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
      .mapTo(this::findDateTimeZone)
      .whenNotFound(failed(new ValidationErrorFailure(
        new ValidationError(ERROR_MESSAGE, Collections.emptyMap()))))
      .fetch(PATH_QUERY);
  }

  private DateTimeZone findDateTimeZone(JsonObject jsonObject) {
    AdjacentConfigurations configurations = new AdjacentConfigurations(jsonObject);
    int totalRecords = configurations.getTotalRecords();
    if (totalRecords <= 0) {
      return DEFAULT_DATE_TIME_ZONE;
    }

    List<TimeZoneConfig> timeZoneConfigs = configurations.getTimeZoneConfigs();
    return timeZoneConfigs.stream()
      .map(this::applyTimeZone)
      .findFirst()
      .orElse(DEFAULT_DATE_TIME_ZONE);
  }

  private DateTimeZone applyTimeZone(TimeZoneConfig item) {
    String value = item.getValue();
    return StringUtils.isBlank(value)
      ? DEFAULT_DATE_TIME_ZONE
      : parseDateTimeZone(value);
  }

  private DateTimeZone parseDateTimeZone(String value) {
    String timezone = new JsonObject(value).getString(TIMEZONE_KEY);
    return StringUtils.isBlank(timezone)
      ? DEFAULT_DATE_TIME_ZONE
      : DateTimeZone.forID(timezone);
  }
}
