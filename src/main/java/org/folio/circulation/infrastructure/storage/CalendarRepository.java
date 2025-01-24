package org.folio.circulation.infrastructure.storage;

import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;

import org.folio.circulation.AdjacentOpeningDays;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.OpeningDay;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class CalendarRepository {

  private static final int PREV_OPENING_INDEX = 0;
  private static final int CURR_OPENING_INDEX = 1;
  private static final int NEXT_OPENING_INDEX = 2;

  private static final String OPENING_INFO_RECORD_TYPE = "openingInfo";

  private static final String SURROUNDING_DATES_KEY = "openings";
  private static final String SURROUNDING_DATES_PATH = "%s/surrounding-openings?date=%s";

  private static final String ALL_DATES_KEY = "dates";
  private static final String ALL_DATES_PATH =
    "%s/all-openings?startDate=%s&endDate=%s&includeClosed=false&limit=%d";

  private final CollectionResourceClient calendarClient;
  private final ConfigurationRepository configurationRepository;

  public CalendarRepository(Clients clients) {
    this.calendarClient = clients.calendarStorageClient();
    this.configurationRepository = new ConfigurationRepository(clients);
  }

  public CompletableFuture<Result<AdjacentOpeningDays>> lookupOpeningDays(
    LocalDate requestedDate, String servicePointId) {
    String path = String.format(SURROUNDING_DATES_PATH, servicePointId, requestedDate);

    // TODO: Validation error should have parameters
    return FetchSingleRecord
      .<AdjacentOpeningDays>forRecord(OPENING_INFO_RECORD_TYPE)
      .using(calendarClient)
      .mapTo(CalendarRepository::convertToOpeningDays)
      .whenNotFound(
        failedValidation(
          new ValidationError("Calendar open periods are not found", Collections.emptyMap())
        )
      )
      .fetch(path);
  }

  public CompletableFuture<Result<Collection<OpeningDay>>> fetchOpeningDaysBetweenDates(
    String servicePointId, ZonedDateTime startDate, ZonedDateTime endDate, ZoneId zoneId) {
    String path = String.format(
      ALL_DATES_PATH,
      servicePointId,
      startDate.withZoneSameInstant(zoneId).toLocalDate(),
      endDate.withZoneSameInstant(zoneId).toLocalDate(),
      Integer.MAX_VALUE
    );

    return calendarClient.get(path)
      .thenCombineAsync(
        configurationRepository.findTimeZoneConfiguration(),
        Result.combined(CalendarRepository::getOpeningDaysFromOpeningDayCollection)
      );
  }

  private static Result<Collection<OpeningDay>> getOpeningDaysFromOpeningDayCollection(
    Response openingDayCollection, ZoneId zone) {
    return MultipleRecords.from(
        openingDayCollection,
        openingPeriod -> new OpeningDay(openingPeriod, zone),
        ALL_DATES_KEY
      )
      .next(r -> Result.succeeded(r.toKeys(UnaryOperator.identity())));
  }

  private static AdjacentOpeningDays convertToOpeningDays(JsonObject jsonObject) {
    if (jsonObject.isEmpty()) {
      return AdjacentOpeningDays.createClosedOpeningDays();
    }

    JsonArray openingDaysJson = jsonObject.getJsonArray(SURROUNDING_DATES_KEY);

    OpeningDay previousDate = new OpeningDay(openingDaysJson.getJsonObject(PREV_OPENING_INDEX));
    OpeningDay requestedDate = new OpeningDay(openingDaysJson.getJsonObject(CURR_OPENING_INDEX));
    OpeningDay nextDate = new OpeningDay(openingDaysJson.getJsonObject(NEXT_OPENING_INDEX));

    return new AdjacentOpeningDays(previousDate, requestedDate, nextDate);
  }
}
