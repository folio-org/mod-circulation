package org.folio.circulation.domain;

import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.policy.LoanPolicyPeriod;
import org.folio.circulation.domain.policy.LoansPolicyProfile;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ServerErrorFailure;
import org.joda.time.DateTime;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.folio.circulation.support.CalendarQueryUtil.collectPathQuery;
import static org.folio.circulation.support.CalendarQueryUtil.collectPathQueryForFixedSchedules;
import static org.folio.circulation.support.HttpResult.failed;

public class CalendarRepository {

  private static final String PATH_PARAM = "%s/period";
  private static final String RECORD_NAME = "openingPeriods";

  private static final HttpResult<LibraryHours> LIBRARY_HOURS_HTTP_RESULT = HttpResult.succeeded(new LibraryHours());
  private static final HttpResult<Calendar> CALENDAR_HTTP_RESULT = HttpResult.succeeded(new Calendar());

  private final CollectionResourceClient resourceClient;

  public CalendarRepository(Clients clients) {
    this.resourceClient = clients.calendarStorageClient();
  }

  /**
   * Get the period of days with an opening endpoint
   * <p>
   * `mod-calendar` API returns 3 days from a current schedule:
   * previous opened, requested and next opened
   */
  public CompletionStage<HttpResult<LoanAndRelatedRecords>> lookupPeriod(HttpResult<LoanAndRelatedRecords> records,
                                                                         String checkoutServicePointId) {
    if (!Objects.isNull(records.cause())) {
      return CompletableFuture.completedFuture(records)
        .exceptionally(e -> failed(new ServerErrorFailure(e)));
    }
    LoanAndRelatedRecords loanAndRelatedRecords = records.value();
    LoanPolicy loanPolicy = loanAndRelatedRecords.getLoanPolicy();
    return CompletableFuture.completedFuture(records)
      .thenCombine(getPeriod(checkoutServicePointId, loanPolicy), this::addCalendar);
  }

  /**
   * Get the closing date of service point
   * <p>
   * When `mod-calendar` API cannot calculate or determine opening endpoints,
   * there may be cases when the app needs to know the opening hours of the service point.
   */
  public CompletionStage<HttpResult<LoanAndRelatedRecords>> lookupLibraryHours(HttpResult<LoanAndRelatedRecords> records,
                                                                               String checkoutServicePointId) {
    if (!Objects.isNull(records.cause())) {
      return CompletableFuture.completedFuture(records)
        .exceptionally(e -> failed(new ServerErrorFailure(e)));
    }
    return CompletableFuture.completedFuture(records)
      .thenCombine(getLibraryHours(checkoutServicePointId), this::addLibraryHours);
  }

  private CompletableFuture<HttpResult<Calendar>> getPeriod(String servicePointId, LoanPolicy loanPolicy) {
    LoanPolicyPeriod interval = loanPolicy.getPeriodInterval();
    int duration = loanPolicy.getPeriodDuration();

    LoansPolicyProfile loansPolicyProfile = loanPolicy.getLoansPolicyProfile();

    String serviceId;
    if (interval == LoanPolicyPeriod.INCORRECT || loansPolicyProfile == LoansPolicyProfile.FIXED) {
      List<DateTime> fixedDueDates = loanPolicy.getFixedDueDates();
      serviceId = collectPathQueryForFixedSchedules(servicePointId, fixedDueDates);
    } else {
      serviceId = collectPathQuery(servicePointId, duration, interval);
    }

    return FetchSingleRecord.<Calendar>forRecord(RECORD_NAME)
      .using(resourceClient)
      .mapTo(jsonObject -> new Calendar(jsonObject, interval, duration))
      .whenNotFound(CALENDAR_HTTP_RESULT)
      .fetch(serviceId);
  }

  private CompletableFuture<HttpResult<LibraryHours>> getLibraryHours(String servicePointId) {
    String serviceId = String.format(PATH_PARAM, servicePointId);
    return FetchSingleRecord.<LibraryHours>forRecord(RECORD_NAME)
      .using(resourceClient)
      .mapTo(LibraryHours::new)
      .whenNotFound(LIBRARY_HOURS_HTTP_RESULT)
      .fetch(serviceId);
  }

  private HttpResult<LoanAndRelatedRecords> addLibraryHours(HttpResult<LoanAndRelatedRecords> loanResult,
                                                            HttpResult<LibraryHours> getLibraryHoursResult) {
    if (Objects.isNull(getLibraryHoursResult)) {
      getLibraryHoursResult = LIBRARY_HOURS_HTTP_RESULT;
    }
    return HttpResult.combine(loanResult, getLibraryHoursResult,
      LoanAndRelatedRecords::withLibraryHours);
  }

  private HttpResult<LoanAndRelatedRecords> addCalendar(HttpResult<LoanAndRelatedRecords> loanResult,
                                                        HttpResult<Calendar> getCalendarResult) {
    if (Objects.isNull(getCalendarResult)) {
      getCalendarResult = CALENDAR_HTTP_RESULT;
    }
    return HttpResult.combine(loanResult, getCalendarResult,
      LoanAndRelatedRecords::withCalendar);
  }
}
