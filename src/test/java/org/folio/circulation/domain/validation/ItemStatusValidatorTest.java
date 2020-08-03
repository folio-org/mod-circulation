package org.folio.circulation.domain.validation;

import static org.folio.circulation.domain.ItemStatus.AGED_TO_LOST;
import static org.folio.circulation.domain.ItemStatus.CLAIMED_RETURNED;
import static org.folio.circulation.domain.ItemStatus.DECLARED_LOST;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ValidationErrorFailure;
import org.junit.Test;

import io.vertx.core.json.JsonObject;
import lombok.val;

public class ItemStatusValidatorTest {

  @Test
  public void cannotCheckOutAgedToLostItem() {
    val validator = new ItemStatusValidator(this::validationError);

    val validationResult  = validator
      .refuseWhenItemIsNotAllowedForCheckOut(loanWithItemInStatus(AGED_TO_LOST));

    assertTrue(validationResult.failed());
    assertThat(validationResult.cause(), instanceOf(ValidationErrorFailure.class));
  }

  @Test
  public void cannotCheckOutClaimedReturnedItem() {
    val validator = new ItemStatusValidator(this::validationError);

    val validationResult  = validator
      .refuseWhenItemIsNotAllowedForCheckOut(loanWithItemInStatus(CLAIMED_RETURNED));

    assertTrue(validationResult.failed());
    assertThat(validationResult.cause(), instanceOf(ValidationErrorFailure.class));
  }

  @Test
  public void cannotCheckOutDeclaredLostItem() {
    val validator = new ItemStatusValidator(this::validationError);

    val validationResult  = validator
      .refuseWhenItemIsNotAllowedForCheckOut(loanWithItemInStatus(DECLARED_LOST));

    assertTrue(validationResult.failed());
    assertThat(validationResult.cause(), instanceOf(ValidationErrorFailure.class));
  }

  private Result<LoanAndRelatedRecords> loanWithItemInStatus(ItemStatus itemStatus) {
    val itemRepresentation = new JsonObject()
      .put("status", new JsonObject().put("name", itemStatus.getValue()));
    val item = Item.from(itemRepresentation);

    val loan = Loan.from(new JsonObject()).withItem(item);
    val context = new LoanAndRelatedRecords(loan);

    return succeeded(context.withItem(item));
  }

  private ValidationErrorFailure validationError(Item item) {
    return singleValidationError("error", "barcode", "some-barcode");
  }
}
