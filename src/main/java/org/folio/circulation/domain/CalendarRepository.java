package org.folio.circulation.domain;

import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.policy.LoanPolicyPeriod;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.StringUtil;
import org.joda.time.DateTime;
import org.joda.time.LocalTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.concurrent.CompletableFuture;

import static org.folio.circulation.resources.CheckOutByBarcodeResource.DATE_TIME_FORMAT;

public class CalendarRepository {

  private static final String RECORD_NAME = "openingPeriods";
  private static final String PATH_PARAM_WITH_QUERY = "%s/calculateopening?requestedDate=%s";

  private static final HttpResult<Calendar> CALENDAR_HTTP_RESULT = HttpResult.succeeded(new Calendar());

  private final CollectionResourceClient resourceClient;
  private final DateTimeFormatter dateTimeFormatter;

  public CalendarRepository(Clients clients) {
    this.resourceClient = clients.calendarStorageClient();
    this.dateTimeFormatter = DateTimeFormat.forPattern(DATE_TIME_FORMAT).withZoneUTC();
  }

  /**
   * Get the period of days with an opening endpoint
   * <p>
   * `mod-calendar` API returns 3 days from a current schedule:
   * previous opened, requested and next opened
   */
  public CompletableFuture<HttpResult<LoanAndRelatedRecords>> lookupPeriod(LoanAndRelatedRecords relatedRecords) {
    return getPeriod(relatedRecords)
      .thenApply(result -> result.map(relatedRecords::withCalendar));
  }

  public CompletableFuture<HttpResult<LoanAndRelatedRecords>> lookupPeriodForFixedDueDateSchedule(
    LoanAndRelatedRecords relatedRecords) {
    return getPeriod(relatedRecords)
      .thenApply(result -> result.map(relatedRecords::withFixedDueDateDays));
  }

  private CompletableFuture<HttpResult<Calendar>> getPeriod(LoanAndRelatedRecords relatedRecords) {
    LoanPolicy loanPolicy = relatedRecords.getLoanPolicy();
    LoanPolicyPeriod interval = loanPolicy.getPeriodInterval();
    int duration = loanPolicy.getPeriodDuration();

    DateTime dueDate = relatedRecords.getLoan().getDueDate();
    String servicePointId = relatedRecords.getLoan().getCheckoutServicePointId();

    String formattedDateTime = dateTimeFormatter.print(dueDate.withTime(LocalTime.MIDNIGHT));
    String encodedDateTime = StringUtil.urlEncode(formattedDateTime);
    String path = String.format(PATH_PARAM_WITH_QUERY, servicePointId, encodedDateTime);

    return FetchSingleRecord.<Calendar>forRecord(RECORD_NAME)
      .using(resourceClient)
      .mapTo(jsonObject -> new Calendar(jsonObject, interval, duration))
      .whenNotFound(CALENDAR_HTTP_RESULT)
      .fetch(path);
  }
}
