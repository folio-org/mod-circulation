package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.HttpResult;
import org.joda.time.DateTimeZone;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ConfigurationRepository {

  private static final DateTimeZone DEFAULT_DATE_TIME_ZONE = DateTimeZone.UTC;
  private static final HttpResult<DateTimeZone> HTTP_RESULT = HttpResult.succeeded(DEFAULT_DATE_TIME_ZONE);

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
      .whenNotFound(HTTP_RESULT)
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
