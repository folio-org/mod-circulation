package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.ValidationErrorFailure;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.json.JsonObject;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import lombok.val;

@RunWith(JUnitParamsRunner.class)
public class ItemStatusValidatorTest {

  @Test
  @Parameters({
    "Declared lost",
    "Claimed returned",
    "Aged to lost"
  })
  public void cannotCheckOutItemInDisallowedStatus(String itemStatus) {
    val validator = new ItemStatusValidator(this::validationError);

    val validationResult  = validator
      .refuseWhenItemIsNotAllowedForCheckOut(loanWithItemInStatus(itemStatus));

    assertTrue(validationResult.failed());
    assertThat(validationResult.cause(), instanceOf(ValidationErrorFailure.class));
  }

  @Test
  @Parameters({
    "Declared lost",
    "Claimed returned",
    "Aged to lost"
  })
  public void cannotChangeDueDateForItemInDisallowedStatus(String itemStatus) {
    val validator = new ItemStatusValidator(this::validationError);

    val validationResult  = validator
      .refuseWhenItemStatusDoesNotAllowDueDateChange(loanWithItemInStatus(itemStatus));

    assertTrue(validationResult.failed());
    assertThat(validationResult.cause(), instanceOf(ValidationErrorFailure.class));
  }

  private Result<LoanAndRelatedRecords> loanWithItemInStatus(String itemStatus) {
    val itemRepresentation = new JsonObject()
      .put("status", new JsonObject().put("name", itemStatus));
    val item = Item.from(itemRepresentation);

    val loan = Loan.from(new JsonObject()).withItem(item);
    val context = new LoanAndRelatedRecords(loan);

    return succeeded(context.withItem(item));
  }

  private ValidationErrorFailure validationError(Item item) {
    return singleValidationError("error", "barcode", "some-barcode");
  }
}
