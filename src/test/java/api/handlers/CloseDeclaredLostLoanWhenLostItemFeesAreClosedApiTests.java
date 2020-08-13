package api.handlers;

import static api.support.matchers.ItemMatchers.isAvailable;
import static api.support.matchers.ItemMatchers.isCheckedOut;
import static api.support.matchers.ItemMatchers.isDeclaredLost;
import static api.support.matchers.ItemMatchers.isLostAndPaid;
import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static api.support.matchers.LoanMatchers.isClosed;
import static api.support.matchers.LoanMatchers.isOpen;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.UUID;

import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.DeclareItemLostRequestBuilder;

public class CloseDeclaredLostLoanWhenLostItemFeesAreClosedApiTests extends APITests {
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

  @After
  public void cleanUp() {
    notesClient.deleteAll();
    noteTypeClient.deleteAll();
  }

  @Test
  public void shouldCloseLoanWhenAllFeesClosed() {
    feeFineAccountFixture.payLostItemFee(loan.getId());
    feeFineAccountFixture.payLostItemProcessingFee(loan.getId());

    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loan.getId());

    assertThat(loansFixture.getLoanById(loan.getId()).getJson(), isClosed());
    assertThat(itemsClient.getById(item.getId()).getJson(), isLostAndPaid());
  }

  @Test
  public void shouldDisregardNonLostFeeTypes() {
    feeFineAccountFixture.payLostItemFee(loan.getId());
    feeFineAccountFixture.payLostItemProcessingFee(loan.getId());

    final IndividualResource manualFee = feeFineAccountFixture
      .createManualFeeForLoan(loan, 10.00);

    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loan.getId());

    assertThat(loansFixture.getLoanById(loan.getId()).getJson(), isClosed());
    assertThat(itemsClient.getById(item.getId()).getJson(), isLostAndPaid());

    assertThat(accountsClient.getById(manualFee.getId()).getJson(), isOpen());
  }

  @Test
  public void shouldNotCloseLoanWhenProcessingFeeIsNotClosed() {
    feeFineAccountFixture.payLostItemFee(loan.getId());

    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loan.getId());

    assertThat(loansFixture.getLoanById(loan.getId()).getJson(), isOpen());
    assertThat(itemsClient.getById(item.getId()).getJson(), isDeclaredLost());
  }

  @Test
  public void shouldNotCloseLoanIfSetCostFeeIsNotClosed() {
    feeFineAccountFixture.payLostItemProcessingFee(loan.getId());

    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loan.getId());

    assertThat(loansFixture.getLoanById(loan.getId()).getJson(), isOpen());
    assertThat(itemsClient.getById(item.getId()).getJson(), isDeclaredLost());
  }

  @Test
  public void shouldNotCloseLoanIfActualCostFeeShouldBeCharged() {
    useLostItemPolicy(lostItemFeePoliciesFixture.create(
      lostItemFeePoliciesFixture.facultyStandardPolicy()
        .chargeProcessingFee(10.00)
        .withActualCost(10.0)).getId());

    feeFineAccountFixture.payLostItemProcessingFee(loan.getId());

    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loan.getId());

    assertThat(loansFixture.getLoanById(loan.getId()).getJson(), isOpen());
    assertThat(itemsClient.getById(item.getId()).getJson(), isDeclaredLost());
  }

  @Test
  public void shouldNotCloseRefundedLoan() {
    feeFineAccountFixture.payLostItemFee(loan.getId());
    feeFineAccountFixture.payLostItemProcessingFee(loan.getId());

    checkInFixture.checkInByBarcode(item);

    assertThat(loansFixture.getLoanById(loan.getId()).getJson(), allOf(
      isClosed(),
      hasJsonPath("action", "checkedin")));
    assertThat(itemsClient.getById(item.getId()).getJson(), isAvailable());

    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loan.getId());

    assertThat(loansFixture.getLoanById(loan.getId()).getJson(), allOf(
      isClosed(),
      hasJsonPath("action", "checkedin")));
    assertThat(itemsClient.getById(item.getId()).getJson(), isAvailable());
  }

  @Test
  public void shouldNotCloseCheckedOutLoan() {
    item = itemsFixture.basedUponNod();
    loan = checkOutFixture.checkOutByBarcode(item, usersFixture.jessica());

    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loan.getId());

    assertThat(loansFixture.getLoanById(loan.getId()).getJson(), isOpen());
    assertThat(itemsClient.getById(item.getId()).getJson(), isCheckedOut());
  }

  @Test
  public void shouldIgnoreErrorWhenNoLoanIdSpecifiedInPayload() {
    final Response response = eventSubscribersFixture
      .attemptPublishLoanRelatedFeeFineClosedEvent(null, UUID.randomUUID());

    assertThat(response.getStatusCode(), is(204));
  }

  @Test
  public void shouldIgnoreErrorWhenNonExistentLoanIdProvided() {
    final UUID loanId = UUID.randomUUID();
    final Response response = eventSubscribersFixture
      .attemptPublishLoanRelatedFeeFineClosedEvent(loanId,
        UUID.randomUUID());

    assertThat(response.getStatusCode(), is(204));
  }
}
