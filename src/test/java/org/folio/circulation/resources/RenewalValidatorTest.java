package org.folio.circulation.resources;

import static api.support.matchers.ResultMatchers.hasValidationError;
import static api.support.matchers.ResultMatchers.succeeded;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static org.folio.circulation.resources.RenewalValidator.errorWhenEarlierOrSameDueDate;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.joda.time.DateTime.now;
import static org.joda.time.DateTimeZone.UTC;

import org.folio.circulation.domain.Loan;
import org.joda.time.DateTime;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;
import lombok.val;

class RenewalValidatorTest {
  @Test
  void shouldDisallowRenewalWhenDueDateIsEarlierOrSame() {
    val dueDate = now(UTC);
    val proposedDueDate = dueDate.minusWeeks(2);
    val loan = createLoan(dueDate);

    val validationResult = errorWhenEarlierOrSameDueDate(loan, proposedDueDate);

    assertThat(validationResult, hasValidationError(
      hasMessage("renewal would not change the due date")));
  }

  @Test
  void shouldAllowRenewalWhenDueDateAfterCurrentDueDate() {
    val dueDate = now(UTC);
    val proposedDueDate = dueDate.plusWeeks(1);
    val loan = createLoan(dueDate);

    val validationResult = errorWhenEarlierOrSameDueDate(loan, proposedDueDate);

    assertThat(validationResult, succeeded());
  }

  private Loan createLoan(DateTime dueDate) {
    return Loan.from(new JsonObject())
      .changeDueDate(dueDate);
  }
}
