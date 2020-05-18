package api.subscribers;

import static api.support.matchers.ItemMatchers.isCheckedOut;
import static api.support.matchers.ItemMatchers.isDeclaredLost;
import static api.support.matchers.ItemMatchers.isLostAndPaid;
import static api.support.matchers.LoanMatchers.isClosed;
import static api.support.matchers.LoanMatchers.isOpen;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasNullParameter;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.UUID;

import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.junit.Before;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.DeclareItemLostRequestBuilder;

public class FeeFineWithLoanClosedSubscriberApiTest extends APITests {
  private IndividualResource loan;
  private IndividualResource item;

  @Before
  public void createLoanAndDeclareItemLost() {
    useLostItemPolicy(lostItemFeePoliciesFixture.chargeFee().getId());

    item = itemsFixture.basedUponSmallAngryPlanet();
    loan = checkOutFixture.checkOutByBarcode(item, usersFixture.jessica());

    declareLostFixtures.declareItemLost(new DeclareItemLostRequestBuilder()
      .forLoanId(loan.getId()));
  }

  @Test
  public void canCloseLoanIfAllFeesClosed() {
    feeFineAccountFixture.payLostItemFee(loan.getId());
    feeFineAccountFixture.payLostItemProcessingFee(loan.getId());

    eventSubscribersFixture.publishFeeFineWithLoanClosedEvent(loan.getId());

    assertThat(loansFixture.getLoanById(loan.getId()).getJson(), isClosed());
    assertThat(itemsClient.getById(item.getId()).getJson(), isLostAndPaid());
  }

  @Test
  public void cannotCloseLoanIfProcessingFeeIsNotClosed() {
    feeFineAccountFixture.payLostItemFee(loan.getId());

    eventSubscribersFixture.publishFeeFineWithLoanClosedEvent(loan.getId());

    assertThat(loansFixture.getLoanById(loan.getId()).getJson(), isOpen());
    assertThat(itemsClient.getById(item.getId()).getJson(), isDeclaredLost());
  }

  @Test
  public void cannotCloseLoanIfItemFeeIsNotClosed() {
    feeFineAccountFixture.payLostItemProcessingFee(loan.getId());

    eventSubscribersFixture.publishFeeFineWithLoanClosedEvent(loan.getId());

    assertThat(loansFixture.getLoanById(loan.getId()).getJson(), isOpen());
    assertThat(itemsClient.getById(item.getId()).getJson(), isDeclaredLost());
  }

  @Test
  public void cannotCloseLoanIfActualCostIsUsed() {
    useLostItemPolicy(lostItemFeePoliciesFixture.create(
      lostItemFeePoliciesFixture.facultyStandardPolicy()
        .chargeProcessingFee(10.00)
        .withActualCost(10.0)).getId());

    feeFineAccountFixture.payLostItemProcessingFee(loan.getId());

    eventSubscribersFixture.publishFeeFineWithLoanClosedEvent(loan.getId());

    assertThat(loansFixture.getLoanById(loan.getId()).getJson(), isOpen());
    assertThat(itemsClient.getById(item.getId()).getJson(), isDeclaredLost());
  }

  @Test
  public void cannotCloseCheckedOutLoan() {
    item = itemsFixture.basedUponNod();
    loan = checkOutFixture.checkOutByBarcode(item, usersFixture.jessica());

    eventSubscribersFixture.publishFeeFineWithLoanClosedEvent(loan.getId());

    assertThat(loansFixture.getLoanById(loan.getId()).getJson(), isOpen());
    assertThat(itemsClient.getById(item.getId()).getJson(), isCheckedOut());
  }

  @Test
  public void cannotPublishEventIfLoanIdDoesNotPresent() {
    final Response response = eventSubscribersFixture
      .attemptPublishFeeFineWithLoanClosedEvent(null, UUID.randomUUID());

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Loan id is required"),
      hasNullParameter("loanId")
    )));
  }

  @Test
  public void cannotPublishEventIfLoanIdDoesNotExist() {
    final UUID loanId = UUID.randomUUID();
    final Response response = eventSubscribersFixture
      .attemptPublishFeeFineWithLoanClosedEvent(loanId,
        UUID.randomUUID());

    assertThat(response.getStatusCode(), is(404));
    assertThat(response.getBody(),
      is(String.format("loan record with ID \"%s\" cannot be found", loanId)));
  }
}
