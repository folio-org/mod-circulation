package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.UUID;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.ValidationErrorFailure;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.json.JsonObject;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import lombok.val;

@RunWith(JUnitParamsRunner.class)
public class ChangeDueDateValidatorTest {
  private ChangeDueDateValidator changeDueDateValidator;

  @Before
  public void mockRepository() {
    changeDueDateValidator = new ChangeDueDateValidator();
  }

  @Test
  @Parameters({
    "Declared lost",
    "Claimed returned",
    "Aged to lost"
  })
  public void cannotChangeDueDateForItemInDisallowedStatus(String itemStatus) {
    val validationResult  = changeDueDateValidator
      .refuseChangeDueDateForItemInDisallowedStatus(loanAndRelatedRecords(itemStatus))
      .getNow(failed(new ServerErrorFailure("timed out")));

    assertTrue(validationResult.failed());
    assertThat(validationResult.cause(), instanceOf(ValidationErrorFailure.class));
  }

  @Test
  @Parameters({
    "Declared lost",
    "Claimed returned",
    "Aged to lost"
  })
  public void canChangeLoanWhenDueDateIsNotChanged(String itemStatus) {
    val existingLoan = createLoan(itemStatus, DateTime.now());

    changeDueDateValidator = new ChangeDueDateValidator();

    val changedLoan = loanAndRelatedRecords(Loan.from(existingLoan.asJson()
      .put("action", "checkedOut")));

    val validationResult  = changeDueDateValidator
      .refuseChangeDueDateForItemInDisallowedStatus(changedLoan)
      .getNow(failed(new ServerErrorFailure("timed out")));

    assertTrue(validationResult.succeeded());
  }

  private Result<LoanAndRelatedRecords> loanAndRelatedRecords(String itemStatus) {
    val loan = createLoan(itemStatus, DateTime.now());
    val existingLoan = createLoan(itemStatus, DateTime.now().minusDays(1));
    return succeeded(new LoanAndRelatedRecords(loan, existingLoan));
  }

  private Result<LoanAndRelatedRecords> loanAndRelatedRecords(Loan loan) {
    return succeeded(new LoanAndRelatedRecords(loan));
  }

  private Loan createLoan(String itemStatus, DateTime dueDate) {
    final JsonObject loanRepresentation = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("dueDate", dueDate.toString());

    val itemRepresentation = new JsonObject()
      .put("status", new JsonObject().put("name", itemStatus));

    val item = Item.from(itemRepresentation);
    return Loan.from(loanRepresentation).withItem(item);
  }
}
