package api.loans;

import static api.support.matchers.ItemMatchers.isAvailable;
import static api.support.matchers.LoanMatchers.isClosed;
import static api.support.matchers.ScheduledNoticeMatchers.hasScheduledLoanNotice;
import static org.folio.circulation.domain.notice.NoticeTiming.AFTER;
import static org.folio.circulation.domain.notice.NoticeTiming.UPON_AT;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.iterableWithSize;

import java.util.List;
import java.util.UUID;

import org.folio.circulation.domain.policy.Period;
import org.joda.time.DateTime;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.NoticeConfigurationBuilder;
import api.support.builders.NoticePolicyBuilder;
import lombok.val;

public class LoanAgedToLostScheduledNoticesProcessingTests extends APITests {
  private static final UUID UPON_AT_TEMPLATE_ID = UUID.randomUUID();
  private static final UUID AFTER_ONE_TIME_TEMPLATE_ID = UUID.randomUUID();
  private static final UUID AFTER_RECURRING_TEMPLATE_ID = UUID.randomUUID();

  private static final Period TIMING_PERIOD = Period.days(1);
  private static final Period RECURRENCE_PERIOD = Period.hours(1);

  @Test
  public void agedToLostNoticesAreCreatedAndProcessed() {
    templateFixture.createDummyNoticeTemplate(UPON_AT_TEMPLATE_ID);
    templateFixture.createDummyNoticeTemplate(AFTER_ONE_TIME_TEMPLATE_ID);
    templateFixture.createDummyNoticeTemplate(AFTER_RECURRING_TEMPLATE_ID);

    val agedToLostLoan = ageToLostFixture.createAgedToLostLoan(
      new NoticePolicyBuilder()
        .active()
        .withName("Aged to lost notice policy")
        .withLoanNotices(List.of(
          new NoticeConfigurationBuilder()
            .withAgedToLostEvent()
            .withTemplateId(UPON_AT_TEMPLATE_ID)
            .withUponAtTiming()
            .create(),
          new NoticeConfigurationBuilder()
            .withAgedToLostEvent()
            .withTemplateId(AFTER_ONE_TIME_TEMPLATE_ID)
            .withAfterTiming(TIMING_PERIOD)
            .create(),
          new NoticeConfigurationBuilder()
            .withAgedToLostEvent()
            .withTemplateId(AFTER_RECURRING_TEMPLATE_ID)
            .withAfterTiming(TIMING_PERIOD)
            .recurring(RECURRENCE_PERIOD)
            .create()
          )));

    final String agedToLostDateString = agedToLostLoan.getLoan()
      .getJson()
      .getJsonObject("agedToLostDelayedBilling")
      .getString("agedToLostDate");

    final DateTime runTimeOfUponAtNotice = DateTime.parse(agedToLostDateString);
    final DateTime runTimeOfAfterNotices = runTimeOfUponAtNotice.plus(TIMING_PERIOD.timePeriod());

    final UUID loanId = agedToLostLoan.getLoanId();

    // before first run, all three scheduled notices should exist
    assertThat(patronNoticesClient.getAll(), hasSize(0));
    assertThat(scheduledNoticesClient.getAll(), allOf(
      iterableWithSize(3),
      hasItems(
        hasScheduledLoanNotice(loanId, runTimeOfUponAtNotice,
          UPON_AT.getRepresentation(), UPON_AT_TEMPLATE_ID, null, true),
        hasScheduledLoanNotice(loanId, runTimeOfAfterNotices,
          AFTER.getRepresentation(), AFTER_ONE_TIME_TEMPLATE_ID, null, true),
        hasScheduledLoanNotice(loanId, runTimeOfAfterNotices,
          AFTER.getRepresentation(), AFTER_RECURRING_TEMPLATE_ID, RECURRENCE_PERIOD, true)
      )));

    // first run, UPON_AT notice should be sent and deleted
    scheduledNoticeProcessingClient.runLoanNoticesProcessing(runTimeOfUponAtNotice.plusMinutes(1));
    assertThat(patronNoticesClient.getAll(), hasSize(1));
    assertThat(scheduledNoticesClient.getAll(), allOf(
      iterableWithSize(2),
      hasItems(
        hasScheduledLoanNotice(loanId, runTimeOfAfterNotices,
          AFTER.getRepresentation(), AFTER_ONE_TIME_TEMPLATE_ID, null, true),
        hasScheduledLoanNotice(loanId, runTimeOfAfterNotices,
          AFTER.getRepresentation(), AFTER_RECURRING_TEMPLATE_ID, RECURRENCE_PERIOD, true)
      )));

    // second run, both AFTER notices should be sent, recurring AFTER notice should be rescheduled
    scheduledNoticeProcessingClient.runLoanNoticesProcessing(runTimeOfAfterNotices.plusMinutes(1));
    assertThat(patronNoticesClient.getAll(), hasSize(3));
    assertThat(scheduledNoticesClient.getAll(), allOf(
      iterableWithSize(1),
      hasItem(
        hasScheduledLoanNotice(loanId, runTimeOfAfterNotices.plus(RECURRENCE_PERIOD.timePeriod()),
          AFTER.getRepresentation(), AFTER_RECURRING_TEMPLATE_ID, RECURRENCE_PERIOD, true)
      )));

    // close the loan
    checkInFixture.checkInByBarcode(agedToLostLoan.getItem());
    assertThat(itemsFixture.getById(agedToLostLoan.getItemId()).getJson(), isAvailable());
    assertThat(loansFixture.getLoanById(agedToLostLoan.getLoanId()).getJson(), isClosed());

    // third run, loan is now closed so recurring notice should be deleted without sending
    scheduledNoticeProcessingClient.runLoanNoticesProcessing(
      runTimeOfAfterNotices.plus(RECURRENCE_PERIOD.timePeriod()).plusMinutes(1));
    assertThat(patronNoticesClient.getAll(), hasSize(3));
    assertThat(scheduledNoticesClient.getAll(), hasSize(0));
  }
}
