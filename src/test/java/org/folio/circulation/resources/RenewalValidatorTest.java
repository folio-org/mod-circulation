package org.folio.circulation.resources;

import static org.folio.circulation.resources.RenewalValidator.errorWhenEarlierOrSameDueDate;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.joda.time.DateTime.now;
import static org.joda.time.DateTimeZone.UTC;
import static org.junit.Assert.assertTrue;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.ValidationErrorFailure;
import org.joda.time.DateTime;
import org.junit.Test;

import io.vertx.core.json.JsonObject;
import lombok.val;

public class RenewalValidatorTest {
  @Test
  public void shouldDisallowRenewalWhenDueDateIsEarlierOrSame() {
    val dueDate = now(UTC);
    val proposedDueDate = dueDate.minusWeeks(2);
    val loan = createLoan(dueDate);

    val validationResult = errorWhenEarlierOrSameDueDate(loan, proposedDueDate);

    assertTrue(validationResult.failed());
    assertThat(validationResult.cause(), instanceOf(ValidationErrorFailure.class));

    val validationFailure = (ValidationErrorFailure) validationResult.cause();
    assertThat(validationFailure.hasErrorWithReason("renewal would not change the due date"),
      is(true));
  }

  @Test
  public void shouldAllowRenewalWhenDueDateAfterCurrentDueDate() {
    val dueDate = now(UTC);
    val proposedDueDate = dueDate.plusWeeks(1);
    val loan = createLoan(dueDate);

    val validationResult = errorWhenEarlierOrSameDueDate(loan, proposedDueDate);

    assertTrue(validationResult.succeeded());
  }

  private Loan createLoan(DateTime dueDate) {
    return Loan.from(new JsonObject())
      .changeDueDate(dueDate);
  }
}
