package api.support.fixtures;

import static api.support.APITestContext.circulationModuleUrl;
import static api.support.APITestContext.getOkapiHeadersFromContext;
import static org.folio.circulation.support.utils.ClockUtil.getClock;
import static org.folio.circulation.support.utils.ClockUtil.setClock;

import java.net.URL;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import api.support.http.TimedTaskClient;

public class ScheduledNoticeProcessingClient {
  private final TimedTaskClient timedTaskClient;

  public ScheduledNoticeProcessingClient() {
    timedTaskClient = new TimedTaskClient(getOkapiHeadersFromContext());
  }

  public void runLoanNoticesProcessing(ZonedDateTime mockSystemTime) {
    runWithFrozenClock(this::runLoanNoticesProcessing, mockSystemTime);
  }

  public void runLoanNoticesProcessing() {
    URL url = circulationModuleUrl(
      "/circulation/loan-scheduled-notices-processing");

    timedTaskClient.start(url, 204,
      "loan-scheduled-notices-processing-request");
  }

  public void runDueDateNotRealTimeNoticesProcessing(ZonedDateTime mockSystemTime) {
    runWithFrozenClock(this::runDueDateNotRealTimeNoticesProcessing, mockSystemTime);
  }

  public void runDueDateNotRealTimeNoticesProcessing() {
    URL url = circulationModuleUrl(
      "/circulation/due-date-not-real-time-scheduled-notices-processing");

    timedTaskClient.start(url, 204,
      "due-date-not-real-time-scheduled-notices-processing-request");
  }

  public void runRequestNoticesProcessing(ZonedDateTime mockSystemTime) {
    runWithFrozenClock(this::runRequestNoticesProcessing, mockSystemTime);
  }

  public void runRequestNoticesProcessing() {
    URL url = circulationModuleUrl(
      "/circulation/request-scheduled-notices-processing");

    timedTaskClient.start(url, 204,
      "request-scheduled-notices-processing-request");
  }

  public void runFeeFineNoticesProcessing(ZonedDateTime mockSystemTime) {
    runWithFrozenClock(this::runFeeFineNoticesProcessing, mockSystemTime);
  }

  public void runFeeFineNotRealTimeNoticesProcessing(ZonedDateTime mockSystemTime) {
    runWithFrozenClock(this::runFeeFineNotRealTimeNoticesProcessing, mockSystemTime);
  }

  public void runOverdueFineNoticesProcessing(ZonedDateTime mockSystemTime) {
    runWithFrozenClock(this::runOverdueFineNoticesProcessing, mockSystemTime);
  }

  public void runFeeFineNoticesProcessing() {
    URL url = circulationModuleUrl(
      "/circulation/fee-fine-scheduled-notices-processing");

    timedTaskClient.start(url, 204,
      "fee-fine-scheduled-notices-processing-request");
  }

  public void runFeeFineNotRealTimeNoticesProcessing() {
    URL url = circulationModuleUrl(
      "/circulation/fee-fine-not-real-time-scheduled-notices-processing");

    timedTaskClient.start(url, 204,
      "fee-fine-not-real-time-scheduled-notices-processing-request");
  }

  public void runOverdueFineNoticesProcessing() {
    URL url = circulationModuleUrl(
      "/circulation/overdue-fine-scheduled-notices-processing");

    timedTaskClient.start(url, 204, "overdue-fine-scheduled-notices-processing");
  }

  public void runScheduledDigitalRemindersProcessing(ZonedDateTime mockSystemTime) {
    runWithFrozenClock(this::runScheduledDigitalRemindersProcessing, mockSystemTime);
  }

  public void runScheduledDigitalRemindersProcessing() {
    URL url = circulationModuleUrl(
      "/circulation/scheduled-reminders-processing");

    timedTaskClient.start(url, 204,
      "scheduled-reminders-processing-request");
  }

  private void runWithFrozenClock(Runnable runnable, ZonedDateTime mockSystemTime) {
    // Save the current clock because it may not be the default clock.
    final Clock original = getClock();

    try {
      setClock(Clock.fixed(mockSystemTime.toInstant(), ZoneOffset.UTC));

      runnable.run();
    } finally {
      setClock(original);
    }
  }

}
