package org.folio.circulation.domain;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.AdjacentOpeningDays;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.server.ValidationError;
import org.joda.time.LocalDate;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import static org.folio.circulation.domain.OpeningDay.createClosedDay;
import static org.folio.circulation.support.HttpResult.failed;

public class CalendarRepository {

  private static final String RECORD_NAME = "openingPeriods";
  private static final String OPENING_DAY = "openingDay";
  private static final String OPENING_DAYS = "openingDays";
  private static final String PATH_PARAM_WITH_QUERY = "%s/calculateopening?requestedDate=%s";

  private final CollectionResourceClient calendarClient;

  public CalendarRepository(Clients clients) {
    this.calendarClient = clients.calendarStorageClient();
  }


  public CompletableFuture<HttpResult<AdjacentOpeningDays>> lookupOpeningDays(LocalDate requestedDate, String servicePointId) {
    String path = String.format(PATH_PARAM_WITH_QUERY, servicePointId, requestedDate);

    return FetchSingleRecord.<AdjacentOpeningDays>forRecord(RECORD_NAME)
      .using(calendarClient)
      .mapTo(this::createOpeningDays)
      .whenNotFound(failed(new ValidationErrorFailure(
        new ValidationError("Calendar open periods are not found", Collections.emptyMap()))))
      .fetch(path);
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
