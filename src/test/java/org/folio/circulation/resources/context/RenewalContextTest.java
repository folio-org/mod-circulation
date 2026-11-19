package org.folio.circulation.resources.context;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import api.support.builders.ItemBuilder;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.Loan;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

class RenewalContextTest {
  @Test
  void shouldSnapshotLoanValuesOnCreate() {
    final JsonObject renewalRequest = new JsonObject().put("itemBarcode", "12345");
    final Loan loan = createLoanWithItemStatus("Checked out");

    final RenewalContext context = RenewalContext.create(loan, renewalRequest, "staff-id");

    assertThat(context.getLoan(), is(loan));
    assertThat(context.getRenewalRequest(), is(renewalRequest));
    assertThat(context.getLoggedInUserId(), is("staff-id"));
    assertThat(context.getLoanBeforeRenewal(), notNullValue());
    assertThat(context.getLoanBeforeRenewal(), not(loan));
    assertThat(context.getItemStatusBeforeRenewal(), is(loan.getItemStatus()));
  }

  @Test
  void shouldSnapshotItemStatusOnCreate() {
    final JsonObject renewalRequest = new JsonObject().put("itemBarcode", "12345");
    final Loan loan = createLoanWithItemStatus("Available");

    final RenewalContext context = RenewalContext.create(loan, renewalRequest, "staff-id");

    assertThat(context.getLoanBeforeRenewal(), notNullValue());
    assertThat(context.getLoanBeforeRenewal(), not(loan));
    assertThat(context.getItemStatusBeforeRenewal(), is(ItemStatus.AVAILABLE));
  }

  private Loan createLoanWithItemStatus(String statusName) {
    final Item item = Item.from(new ItemBuilder().withStatus(statusName).create());

    return Loan.from(new JsonObject().put("itemStatus", statusName)).withItem(item);
  }
}
