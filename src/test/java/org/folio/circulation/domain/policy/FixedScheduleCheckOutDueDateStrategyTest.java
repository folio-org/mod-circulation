package org.folio.circulation.domain.policy;

import api.support.builders.LoanBuilder;
import static java.time.ZoneOffset.UTC;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.server.ValidationError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FixedScheduleCheckOutDueDateStrategyTest {

  private static final UUID ITEM_ID = UUID.randomUUID();
  private static final UUID CHECKOUT_SERVICE_POINT_ID = UUID.randomUUID();
  public static final ZonedDateTime LOAN_DATE = ZonedDateTime.of(2018, 1, 20, 13, 45, 21, 0, UTC);
  public static final ZonedDateTime DUE_DATE = ZonedDateTime.of(2018, 1, 31, 23, 59, 59, 0, UTC);

  @Mock private FixedDueDateSchedules fixedDueDateSchedules;
  @InjectMocks private FixedScheduleCheckOutDueDateStrategy strategy;

  @BeforeEach
  void setUp() {
    strategy = new FixedScheduleCheckOutDueDateStrategy(
      "testLoanPolicyId", "testLoanPolicyName", fixedDueDateSchedules, ValidationError::new);
  }

  @Test
  void shouldReturnDueDateWhenScheduleIsFound() {
    var loan = existingLoan();

    when(fixedDueDateSchedules.findDueDateFor(LOAN_DATE)).thenReturn(Optional.of(DUE_DATE));

    var result = strategy.calculateDueDate(loan);

    assertThat(result.succeeded(), is(true));
    assertThat(result.value(), is(DUE_DATE));
  }

  @Test
  void shouldReturnFailedResultWhenScheduleIsNotFound() {
    var loan = existingLoan();

    when(fixedDueDateSchedules.findDueDateFor(LOAN_DATE)).thenReturn(Optional.empty());

    var result = strategy.calculateDueDate(loan);

    assertThat(result.failed(), is(true));

    var cause = (ValidationErrorFailure) result.cause();
    var expectedReason = "loan date falls outside of the date ranges in the loan policy";
    assertThat(cause.hasErrorWithReason(expectedReason), is(true));
  }

  @Test
  void shouldReturnFailedResultWhenExceptionOccurred() {
    var loan = existingLoan();

    var cause = new RuntimeException("Failed to find due date");
    when(fixedDueDateSchedules.findDueDateFor(LOAN_DATE)).thenThrow(cause);

    var result = strategy.calculateDueDate(loan);

    assertThat(result.failed(), is(true));

    var validationErrorFailure = (ServerErrorFailure) result.cause();
    assertThat(validationErrorFailure.getReason(), startsWith("Failed to find due date"));
  }

  private static Loan existingLoan() {
    return new LoanBuilder()
      .open()
      .withLoanDate(LOAN_DATE)
      .withCheckoutServicePointId(CHECKOUT_SERVICE_POINT_ID)
      .withItemId(ITEM_ID)
      .asDomainObject();
  }
}
