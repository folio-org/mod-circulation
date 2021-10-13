package org.folio.circulation.resources;

import static api.support.matchers.ResultMatchers.hasValidationError;
import static api.support.matchers.ResultMatchers.succeeded;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static org.folio.circulation.resources.RenewalValidator.errorWhenEarlierOrSameDueDate;
import static org.folio.circulation.support.utils.ClockUtil.getZonedDateTime;
import static org.hamcrest.MatcherAssert.assertThat;

import java.time.ZonedDateTime;

import org.folio.circulation.domain.Loan;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;
import lombok.val;

class RenewalValidatorTest {
  @Test
  void shouldDisallowRenewalWhenDueDateIsEarlierOrSame() {
    val dueDate = getZonedDateTime();
    val proposedDueDate = dueDate.minusWeeks(2);
    val loan = createLoan(dueDate);

    val validationResult = errorWhenEarlierOrSameDueDate(loan, proposedDueDate);

    assertThat(validationResult, hasValidationError(
      hasMessage("renewal would not change the due date")));
  }

  @Test
  void shouldAllowRenewalWhenDueDateAfterCurrentDueDate() {
    val dueDate = getZonedDateTime();
    val proposedDueDate = dueDate.plusWeeks(1);
    val loan = createLoan(dueDate);

    val validationResult = errorWhenEarlierOrSameDueDate(loan, proposedDueDate);

    assertThat(validationResult, succeeded());
  }

  private Loan createLoan(ZonedDateTime dueDate) {
    return Loan.from(new JsonObject())
      .changeDueDate(dueDate);
  }
}
