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
  void shouldPreserveProvidedPerformanceStateAndSnapshotLoanValues() {
    final JsonObject renewalRequest = new JsonObject().put("itemBarcode", "12345");
    final Loan loan = createLoanWithItemStatus("Checked out");

    final RenewalContext context = RenewalContext.create(loan, renewalRequest,
      "staff-id", "analysis-id", 1234L);

    assertThat(context.getLoan(), is(loan));
    assertThat(context.getRenewalRequest(), is(renewalRequest));
    assertThat(context.getLoggedInUserId(), is("staff-id"));
    assertThat(context.getPerformanceAnalysisId(), is("analysis-id"));
    assertThat(context.getLastPerformanceTimestampMillis(), is(1234L));
    assertThat(context.getLoanBeforeRenewal(), notNullValue());
    assertThat(context.getLoanBeforeRenewal(), not(loan));
    assertThat(context.getItemStatusBeforeRenewal(), is(loan.getItemStatus()));
  }

  @Test
  void shouldGeneratePerformanceStateAndSnapshotLoanValuesForConvenienceFactory() {
    final JsonObject renewalRequest = new JsonObject().put("itemBarcode", "12345");
    final Loan loan = createLoanWithItemStatus("Available");

    final RenewalContext context = RenewalContext.create(loan, renewalRequest, "staff-id");

    assertThat(context.getPerformanceAnalysisId(), notNullValue());
    assertThat(context.getLastPerformanceTimestampMillis(), notNullValue());
    assertThat(context.getLoanBeforeRenewal(), notNullValue());
    assertThat(context.getLoanBeforeRenewal(), not(loan));
    assertThat(context.getItemStatusBeforeRenewal(), is(ItemStatus.AVAILABLE));
  }

  @Test
  void shouldIgnorePerformanceStateInEqualityAndHashCode() {
    final JsonObject renewalRequest = new JsonObject().put("itemBarcode", "12345");
    final Loan loan = createLoanWithItemStatus("Checked out");

    final RenewalContext firstContext = RenewalContext.create(loan, renewalRequest,
      "staff-id", "analysis-id-1", 1234L);

    final RenewalContext secondContext = firstContext
      .withPerformanceAnalysisId("analysis-id-2")
      .withLastPerformanceTimestampMillis(5678L);

    assertThat(firstContext, is(secondContext));
    assertThat(firstContext.hashCode(), is(secondContext.hashCode()));
  }

  private Loan createLoanWithItemStatus(String statusName) {
    final Item item = Item.from(new ItemBuilder().withStatus(statusName).create());

    return Loan.from(new JsonObject().put("itemStatus", statusName)).withItem(item);
  }
}
