package org.folio.circulation.domain.validation;

import static java.util.Arrays.asList;
import static org.folio.circulation.support.results.Result.of;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.folio.circulation.AdjacentOpeningDays;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.OpeningDay;
import org.folio.circulation.domain.OpeningHour;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.infrastructure.storage.CalendarRepository;
import org.folio.circulation.support.results.Result;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import api.support.builders.ItemBuilder;
import api.support.builders.LoanBuilder;
import api.support.builders.LoanPolicyBuilder;
import api.support.builders.RequestBuilder;

@RunWith(MockitoJUnitRunner.class)
public class LoanPolicyValidatorTests {
  private static final LocalDate PREVIOUS_DATE = new LocalDate(2018, 1, 1);
  private static final LocalDate CURRENT_DATE = new LocalDate(2018, 1, 2);
  private static final LocalDate NEXT_DATE = new LocalDate(2018, 1, 3);

  private static final LocalTime CURRENT_TIME = new LocalTime()
    .withHourOfDay(11)
    .withMinuteOfHour(50);

  private static final Period ROLLING_PERIOD = Period.months(1);

  @Mock
  private CalendarRepository calendarRepository;

  @Before
  public void setUp() {
    mockOpenings();
  }

  @Test
  public void hasNoTimetable() {
    final LoanPolicyValidator validator = new LoanPolicyValidator(calendarRepository);
    final DateTime loanDate = new DateTime()
      .withDate(CURRENT_DATE)
      .withTime(CURRENT_TIME);

    final Result<LoanAndRelatedRecords> records = of(
      () -> appendTimetable(generateLoanAndRelatedRecords(loanDate)));
    final Result<LoanAndRelatedRecords> result =
      validator.refuseWhenLoanPolicyHasNoTimetable(records);

    assertThat(result.succeeded(), is(false));
  }

  @Test
  public void hasTimetable() {
    final LoanPolicyValidator validator = new LoanPolicyValidator(calendarRepository);
    final DateTime loanDate = new DateTime()
      .withDate(CURRENT_DATE)
      .withTime(CURRENT_TIME)
      .minusMinutes(ROLLING_PERIOD.toMinutes());

    final Result<LoanAndRelatedRecords> records = of(
      () -> appendTimetable(generateLoanAndRelatedRecords(loanDate)));
    final Result<LoanAndRelatedRecords> result =
      validator.refuseWhenLoanPolicyHasNoTimetable(records);

    assertThat(result.succeeded(), is(true));
  }

  private LoanAndRelatedRecords generateLoanAndRelatedRecords(DateTime loanDate) {
    final Loan loan = new LoanBuilder()
      .open()
      .withLoanDate(loanDate)
      .asDomainObject();

    final Item item = Item.from(new ItemBuilder()
      .checkOut()
      .withId(UUID.randomUUID())
      .create());

    return new LoanAndRelatedRecords(loan.withItem(item));
  }

  private LoanAndRelatedRecords appendTimetable(LoanAndRelatedRecords loanAndRelatedRecords) {
    final Request requestOne = Request.from(new RequestBuilder()
      .withId(UUID.randomUUID())
      .withPosition(1)
      .create());

    final Request requestTwo = Request.from(new RequestBuilder()
      .withId(UUID.randomUUID())
      .withStatus(RequestStatus.OPEN_NOT_YET_FILLED.getValue())
      .hold()
      .withItemId(UUID.fromString(loanAndRelatedRecords.getLoan().getItemId()))
      .withPosition(2)
      .create());

    final RequestQueue requestQueue = new RequestQueue(asList(requestOne, requestTwo));

    return appendLoan(loanAndRelatedRecords)
      .withRequestQueue(requestQueue)
      .withTimeZone(DateTimeZone.UTC);
  }

  private LoanAndRelatedRecords appendLoan(LoanAndRelatedRecords loanAndRelatedRecords) {
    final LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .rolling(ROLLING_PERIOD)
      .create());

    final Loan loan = loanAndRelatedRecords.getLoan().withLoanPolicy(loanPolicy);

    return loanAndRelatedRecords
      .withLoan(loan);
  }

  private void mockOpenings() {
    final List<OpeningHour> openingHour = createOpeningHour();
    final OpeningDay previousOpening = createOpeningDay(openingHour, PREVIOUS_DATE);
    final OpeningDay currentOpening = createOpeningDay(openingHour, CURRENT_DATE);
    final OpeningDay nextOpening = createOpeningDay(openingHour, NEXT_DATE);

    final AdjacentOpeningDays adjacentOpeningDays
      = new AdjacentOpeningDays(previousOpening, currentOpening, nextOpening);

    when(calendarRepository.lookupOpeningDays(any(), any()))
      .thenReturn(ofAsync(() -> adjacentOpeningDays));
  }

  private OpeningDay createOpeningDay(List<OpeningHour> openingHour, LocalDate date) {
    return OpeningDay.createOpeningDay(openingHour, date, true, true);
  }

  private List<OpeningHour> createOpeningHour() {
    return Collections.singletonList(new OpeningHour(null, null));
  }

}
