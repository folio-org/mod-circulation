package org.folio.circulation.domain;

import static org.folio.circulation.domain.OpeningDay.createClosedDay;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.folio.circulation.AdjacentOpeningDays;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.server.ValidationError;
import org.joda.time.LocalDate;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class CalendarRepository {

  private static final String OPENING_PERIODS = "openingPeriods";
  private static final String OPENING_DAY = "openingDay";
  private static final String OPENING_DAYS = "openingDays";
  private static final String PATH_PARAM_WITH_QUERY = "%s/calculateopening?requestedDate=%s";
  private static final String PERIODS_QUERY_PARAMS = "?servicePointId=%s&startDate=%s&endDate=%s";

  private final CollectionResourceClient calendarClient;

  public CalendarRepository(Clients clients) {
    this.calendarClient = clients.calendarStorageClient();
  }


  public CompletableFuture<Result<AdjacentOpeningDays>> lookupOpeningDays(LocalDate requestedDate, String servicePointId) {
    String path = String.format(PATH_PARAM_WITH_QUERY, servicePointId, requestedDate);

    //TODO: Validation error should have parameters
    return FetchSingleRecord.<AdjacentOpeningDays>forRecord(OPENING_PERIODS)
      .using(calendarClient)
      .mapTo(this::createOpeningDays)
      .whenNotFound(failedValidation(
        new ValidationError("Calendar open periods are not found", Collections.emptyMap())))
      .fetch(path);
  }

  public CompletableFuture<Result<List<OpeningDay>>> fetchOpeningDays(
      LocalDate startDate, LocalDate endDate, String servicePointId) {

    String path = String.format(PERIODS_QUERY_PARAMS, servicePointId, startDate, endDate);
    return FetchSingleRecord.<List<OpeningDay>>forRecord(OPENING_PERIODS)
      .using(calendarClient)
      .mapTo(this::createOpeningDaysForPeriod)
      .whenNotFound(failedValidation(
        new ValidationError("Calendar open periods are not found", Collections.emptyMap())))
      .fetch(path);
  }

  private List<OpeningDay> createOpeningDaysForPeriod(JsonObject jsonObject) {
    if (jsonObject.isEmpty()) {
      return Collections.emptyList();
    }
    JsonArray openingDaysJson = jsonObject.getJsonArray(OPENING_PERIODS);
    return IntStream.range(0, openingDaysJson.size())
      .mapToObj(openingDaysJson::getJsonObject)
      .map(day -> new OpeningDay(day, OPENING_DAY))
      .collect(Collectors.toList());
  }

  private AdjacentOpeningDays createOpeningDays(JsonObject jsonObject) {
    if (jsonObject.isEmpty()) {
      return buildClosedOpeningDays();
    }
    JsonArray openingDaysJson = jsonObject.getJsonArray(OPENING_DAYS);
    if (openingDaysJson.isEmpty()) {
      return buildClosedOpeningDays();
    }
    OpeningDay previousDate = new OpeningDay(openingDaysJson.getJsonObject(0), OPENING_DAY);
    OpeningDay requestedDate = new OpeningDay(openingDaysJson.getJsonObject(1), OPENING_DAY);
    OpeningDay nextDate = new OpeningDay(openingDaysJson.getJsonObject(2), OPENING_DAY);

    return new AdjacentOpeningDays(previousDate, requestedDate, nextDate);
  }

  private AdjacentOpeningDays buildClosedOpeningDays() {
    OpeningDay closedDay = createClosedDay();
    return new AdjacentOpeningDays(closedDay, closedDay, closedDay);
  }

}
