package org.folio.circulation.domain;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.AdjustingOpeningDays;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.HttpResult;
import org.joda.time.DateTime;

import java.util.concurrent.CompletableFuture;

public class CalendarRepository {

  private static final String RECORD_NAME = "openingPeriods";
  private static final String OPENING_DAY = "openingDay";
  private static final String OPENING_DAYS = "openingDays";
  private static final String PATH_PARAM_WITH_QUERY = "%s/calculateopening?startDate=%s&unit=%s&amount=%s";

  private static final HttpResult<AdjustingOpeningDays> CALENDAR_HTTP_RESULT = HttpResult.succeeded(null);

  private final CollectionResourceClient resourceClient;

  public CalendarRepository(Clients clients) {
    this.resourceClient = clients.calendarStorageClient();
  }


  public CompletableFuture<HttpResult<AdjustingOpeningDays>> lookupOpeningDays(Loan loan) {
    DateTime requestedDate = loan.getDueDate();
    String servicePointId = loan.getCheckoutServicePointId();

    //replace after calendar api change
    String path = String.format(PATH_PARAM_WITH_QUERY, servicePointId, requestedDate.toLocalDate(), "hour", 1);

    return FetchSingleRecord.<AdjustingOpeningDays>forRecord(RECORD_NAME)
      .using(resourceClient)
      .mapTo(this::createOpeningDays)
      .whenNotFound(CALENDAR_HTTP_RESULT)
      .fetch(path);
  }

  private AdjustingOpeningDays createOpeningDays(JsonObject jsonObject) {
    if (jsonObject.isEmpty()) {
      return null;
    }
    JsonArray openingDaysJson = jsonObject.getJsonArray(OPENING_DAYS);
    if (openingDaysJson.isEmpty()) {
      return null;
    }
    OpeningDay previousDate = new OpeningDay(openingDaysJson.getJsonObject(0), OPENING_DAY);
    OpeningDay requestedDate = new OpeningDay(openingDaysJson.getJsonObject(1), OPENING_DAY);
    OpeningDay nextDate = new OpeningDay(openingDaysJson.getJsonObject(2), OPENING_DAY);

    return new AdjustingOpeningDays(previousDate, requestedDate, nextDate);
  }
}
