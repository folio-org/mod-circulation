package org.folio.circulation.domain;

import org.folio.circulation.domain.policy.DueDateManagement;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.policy.LoanPolicyPeriod;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ServerErrorFailure;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.folio.circulation.support.HttpResult.failed;
import static org.folio.circulation.support.CalendarQueryUtil.collectPathQuery;

public class CalendarRepository {

  private static final String PATH_PARAM = "%s/period";
  private static final String RECORD_NAME = "openingPeriods";

  private final CollectionResourceClient resourceClient;

  public CalendarRepository(Clients clients) {
    this.resourceClient = clients.calendarStorageClient();
  }

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
    LoanPolicyPeriod offsetPeriodInterval = loanPolicy.getOffsetPeriodInterval();
    int offsetDuration = loanPolicy.getOffsetPeriodDuration();
    DueDateManagement dueDateManagement = loanPolicy.getDueDateManagement();

    String serviceId = collectPathQuery(servicePointId, duration, interval, dueDateManagement,
      offsetDuration, offsetPeriodInterval);

    return FetchSingleRecord.<Calendar>forRecord(RECORD_NAME)
      .using(resourceClient)
      .mapTo(jsonObject -> new Calendar(jsonObject, interval, duration))
      .whenNotFound(HttpResult.succeeded(new Calendar()))
      .fetch(serviceId);
  }

  private CompletableFuture<HttpResult<LibraryHours>> getLibraryHours(String servicePointId) {
    String serviceId = String.format(PATH_PARAM, servicePointId);
    return FetchSingleRecord.<LibraryHours>forRecord(RECORD_NAME)
      .using(resourceClient)
      .mapTo(LibraryHours::new)
      .whenNotFound(HttpResult.succeeded(new LibraryHours()))
      .fetch(serviceId);
  }

  private HttpResult<LoanAndRelatedRecords> addLibraryHours(HttpResult<LoanAndRelatedRecords> loanResult,
                                                            HttpResult<LibraryHours> getLibraryHoursResult) {
    if (Objects.isNull(getLibraryHoursResult)) {
      getLibraryHoursResult = HttpResult.succeeded(new LibraryHours());
    }
    return HttpResult.combine(loanResult, getLibraryHoursResult,
      LoanAndRelatedRecords::withLibraryHours);
  }

  private HttpResult<LoanAndRelatedRecords> addCalendar(HttpResult<LoanAndRelatedRecords> loanResult,
                                                        HttpResult<Calendar> getCalendarResult) {
    if (Objects.isNull(getCalendarResult)) {
      getCalendarResult = HttpResult.succeeded(new Calendar());
    }
    return HttpResult.combine(loanResult, getCalendarResult,
      LoanAndRelatedRecords::withCalendar);
  }
}
