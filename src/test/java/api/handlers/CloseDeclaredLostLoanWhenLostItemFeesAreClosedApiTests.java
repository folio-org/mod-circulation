package api.handlers;

import static api.support.fakes.FakePubSub.getPublishedEvents;
import static api.support.fakes.FakePubSub.getPublishedEventsAsList;
import static api.support.fakes.PublishedEvents.byEventType;
import static api.support.matchers.EventMatchers.isValidLoanClosedEvent;
import static api.support.matchers.ItemMatchers.isAvailable;
import static api.support.matchers.ItemMatchers.isCheckedOut;
import static api.support.matchers.ItemMatchers.isDeclaredLost;
import static api.support.matchers.ItemMatchers.isLostAndPaid;
import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static api.support.matchers.LoanMatchers.isClosed;
import static api.support.matchers.LoanMatchers.isOpen;
import static org.folio.circulation.domain.EventType.LOAN_CLOSED;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;

import java.util.UUID;

import api.support.http.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.builders.DeclareItemLostRequestBuilder;
import io.vertx.core.json.JsonObject;

class CloseDeclaredLostLoanWhenLostItemFeesAreClosedApiTests extends APITests {
  private IndividualResource loan;
  private IndividualResource item;

  @BeforeEach
  public void createLoanAndDeclareItemLost() {
    UUID servicePointId = servicePointsFixture.cd1().getId();
    useLostItemPolicy(lostItemFeePoliciesFixture.chargeFee().getId());

    item = itemsFixture.basedUponSmallAngryPlanet();
    loan = checkOutFixture.checkOutByBarcode(item, usersFixture.jessica());

    declareLostFixtures.declareItemLost(new DeclareItemLostRequestBuilder()
      .withServicePointId(servicePointId)
      .forLoanId(loan.getId()));
  }

  @Test
  void shouldCloseLoanWhenAllFeesClosed() {
    feeFineAccountFixture.payLostItemFee(loan.getId());
    feeFineAccountFixture.payLostItemProcessingFee(loan.getId());

    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loan.getId());

    assertThat(loansFixture.getLoanById(loan.getId()).getJson(), isClosed());
    assertThat(itemsClient.getById(item.getId()).getJson(), isLostAndPaid());

    assertThat(getPublishedEvents().findFirst(byEventType(LOAN_CLOSED)),
      isValidLoanClosedEvent(loan.getJson()));
  }

  @Test
  void shouldDisregardNonLostFeeTypes() {
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
  void shouldNotCloseLoanWhenProcessingFeeIsNotClosed() {
    feeFineAccountFixture.payLostItemFee(loan.getId());

    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loan.getId());

    assertThat(loansFixture.getLoanById(loan.getId()).getJson(), isOpen());
    assertThat(itemsClient.getById(item.getId()).getJson(), isDeclaredLost());
  }

  @Test
  void shouldNotCloseLoanIfSetCostFeeIsNotClosed() {
    feeFineAccountFixture.payLostItemProcessingFee(loan.getId());

    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loan.getId());

    assertThat(loansFixture.getLoanById(loan.getId()).getJson(), isOpen());
    assertThat(itemsClient.getById(item.getId()).getJson(), isDeclaredLost());
  }

  @Test
  void shouldNotCloseLoanIfActualCostFeeShouldBeCharged() {
    useLostItemPolicy(lostItemFeePoliciesFixture.create(
      lostItemFeePoliciesFixture.facultyStandardPolicy()
        .chargeProcessingFeeWhenDeclaredLost(10.00)
        .withActualCost(10.0)).getId());

    feeFineAccountFixture.payLostItemProcessingFee(loan.getId());

    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loan.getId());

    assertThat(loansFixture.getLoanById(loan.getId()).getJson(), isOpen());
    assertThat(itemsClient.getById(item.getId()).getJson(), isDeclaredLost());
  }

  @Test
  void shouldNotCloseRefundedLoan() {
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
  void shouldNotCloseCheckedOutLoan() {
    item = itemsFixture.basedUponNod();
    loan = checkOutFixture.checkOutByBarcode(item, usersFixture.jessica());

    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loan.getId());

    assertThat(loansFixture.getLoanById(loan.getId()).getJson(), isOpen());
    assertThat(itemsClient.getById(item.getId()).getJson(), isCheckedOut());
  }

  @Test
  void shouldIgnoreErrorWhenNoLoanIdSpecifiedInPayload() {
    final Response response = eventSubscribersFixture
      .attemptPublishLoanRelatedFeeFineClosedEvent(null, UUID.randomUUID());

    assertThat(response.getStatusCode(), is(204));
  }

  @Test
  void shouldIgnoreErrorWhenNonExistentLoanIdProvided() {
    final UUID loanId = UUID.randomUUID();
    final Response response = eventSubscribersFixture
      .attemptPublishLoanRelatedFeeFineClosedEvent(loanId,
        UUID.randomUUID());

    assertThat(response.getStatusCode(), is(204));
  }

  @Test
  void shouldNotPublishLoanClosedEventWhenLoanIsOriginallyClosed() {
    feeFineAccountFixture.payLostItemFee(loan.getId());
    feeFineAccountFixture.payLostItemProcessingFee(loan.getId());

    JsonObject loanToClose = loansStorageClient.get(loan).getJson();
    loanToClose.getJsonObject("status").put("name", "Closed");
    loansStorageClient.replace(loan.getId(), loanToClose);

    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loan.getId());

    assertThat(loansFixture.getLoanById(loan.getId()).getJson(), isClosed());
    assertThat(itemsClient.getById(item.getId()).getJson(), isLostAndPaid());

    assertThat(getPublishedEventsAsList(byEventType(LOAN_CLOSED)), empty());
  }
}
