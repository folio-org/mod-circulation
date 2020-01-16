package org.folio.circulation.domain;

import static java.util.function.Function.identity;
import static org.folio.circulation.domain.OpeningDay.createClosedDay;
import static org.folio.circulation.support.ResultBinding.flatMapResult;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.AdjacentOpeningDays;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.server.ValidationError;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class CalendarRepository {

  private static final String OPENING_PERIODS = "openingPeriods";
  private static final String OPENING_DAY = "openingDay";
  private static final String OPENING_DAYS = "openingDays";
  private static final String PATH_PARAM_WITH_QUERY = "%s/calculateopening?requestedDate=%s";
  private static final String PERIODS_QUERY_PARAMS =
    "startDate=%sendDate=%s&servicePointId=%s&actualOpening=%s&includeClosedDays=%s";

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

  public CompletableFuture<Result<List<OpeningDay>>> fetchOpeningPeriodsBetweenDates(
      DateTime startDate, DateTime endDate, String servicePointId, boolean actualOpening, boolean includeClosedDays) {

    String params = String.format(PERIODS_QUERY_PARAMS,
      startDate.toLocalDate(), endDate.toLocalDate(), servicePointId, actualOpening, includeClosedDays);
    return calendarClient.getManyWithRawQueryStringParameters(params)
      .thenApply(flatMapResult(this::mapPeriodsResponseToOpeningDays));
  }

  private Result<List<OpeningDay>> mapPeriodsResponseToOpeningDays(Response response) {
    return MultipleRecords.from(response, OpeningDay::fromOpeningPeriod, OPENING_PERIODS)
      .next(r -> Result.succeeded(r.toKeys(identity())));
  }

//  private Result<List<OpeningDay>> mapPeriodsResponseToOpeningDays(Response response) {
//    JsonObject responseJson = response.getJson();
//    if (responseJson.isEmpty()) {
//      return succeeded(Collections.emptyList());
//    }
//    JsonArray openingDaysJson = responseJson.getJsonArray(OPENING_PERIODS);
//    List<OpeningDay> openingDays = IntStream.range(0, openingDaysJson.size())
//      .mapToObj(openingDaysJson::getJsonObject)
//      .map(OpeningDay::fromOpeningPeriod)
//      .collect(Collectors.toList());
//
//    return succeeded(openingDays);
//  }

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
