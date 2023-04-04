package api.loans.scenarios;

import static api.support.PubsubPublisherTestUtils.assertThatPublishedLoanLogRecordEventsAreValid;
import static api.support.fixtures.TemplateContextMatchers.getFeeChargeAdditionalInfoContextMatcher;
import static api.support.matchers.AccountMatchers.isOpen;
import static api.support.matchers.ItemMatchers.isAvailable;
import static api.support.matchers.LoanAccountMatcher.hasLostItemFee;
import static api.support.matchers.LoanAccountMatcher.hasLostItemFeeActualCost;
import static api.support.matchers.LoanAccountMatcher.hasLostItemProcessingFee;
import static api.support.matchers.PatronNoticeMatcher.hasEmailNoticeProperties;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasParameter;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfScheduledNotices;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfSentNotices;
import static java.time.Duration.ofMinutes;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.utils.ClockUtil;
import org.junit.jupiter.api.Test;

import api.support.builders.CheckInByBarcodeRequestBuilder;
import api.support.builders.DeclareItemLostRequestBuilder;
import api.support.builders.NoticeConfigurationBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.fakes.FakeModNotify;
import api.support.fixtures.policies.PoliciesToActivate;
import api.support.http.IndividualResource;
import api.support.http.UserResource;
import io.vertx.core.json.JsonObject;

class CheckInDeclaredLostItemTest extends RefundDeclaredLostFeesTestBase {
  public CheckInDeclaredLostItemTest() {
    super("Cancelled item returned", "Lost item was returned");
  }

  @Override
  protected void performActionThatRequiresRefund(ZonedDateTime actionDate) {
    mockClockManagerToReturnFixedDateTime(actionDate);

    final JsonObject loan = checkInFixture.checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(item)
      .at(servicePointsFixture.cd1())
      .on(actionDate))
      .getLoan();

    assertThat(loan, notNullValue());
  }

  @Test
  void shouldRefundOnlyLastLoanForLostAndPaidItem() {
    final double firstFee = 20.00;
    final double secondFee = 30.00;

    useChargeableRefundableLostItemFee(firstFee, 0.0);

    final IndividualResource firstLoan = declareItemLost();
    mockClockManagerToReturnFixedDateTime(ClockUtil.getZonedDateTime()
      .plusMinutes(2));
    // Item fee won't be cancelled, because refund period is exceeded
    checkInFixture.checkInByBarcode(item);
    assertThat(itemsClient.getById(item.getId()).getJson(), isAvailable());

    mockClockManagerToReturnDefaultDateTime();
    declareItemLost(secondFee);
    resolveLostItemFee();
    checkInFixture.checkInByBarcode(item);

    assertThat(firstLoan, hasLostItemFee(isOpen(firstFee)));
    assertThat(loan, hasLostItemFee(isClosedCancelled(secondFee)));

    assertThatPublishedLoanLogRecordEventsAreValid(loansClient.getById(loan.getId()).getJson());
  }

  @Test
  void shouldFailIfNoLoanForLostAndPaidItem() {
    final double setCost = 20.00;

    declareItemLost(setCost);
    resolveLostItemFee();

    // Remove the loan from storage
    loansFixture.deleteLoan(loan.getId());

    final Response checkInResponse = checkInFixture.attemptCheckInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .at(servicePointsFixture.cd1())
        .forItem(item));

    assertThat(checkInResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Item is lost however there is no aged to lost nor declared lost loan found"),
      hasParameter("itemId", item.getId().toString()))));
  }

  @Test
  void shouldFailIfLastLoanIsNotDeclaredLostForLostAndPaidItem() {
    loan = checkOutFixture.checkOutByBarcode(item, usersFixture.jessica());

    checkInFixture.checkInByBarcode(item);

    // Update the item status in storage
    itemsClient.replace(item.getId(), itemsClient.get(item).getJson().copy()
      .put("status", new JsonObject().put("name", "Lost and paid")));

    final Response checkInResponse = checkInFixture.attemptCheckInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .at(servicePointsFixture.cd1())
        .forItem(item));

    assertThat(checkInResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Last loan for lost item is neither aged to lost nor declared lost"),
      hasParameter("loanId", loan.getId().toString()))));
  }

  @Test
  void shouldRefundPaidAmountForLostAndPaidItem() {
    final double setCostFee = 10.00;

    declareItemLost(setCostFee);
    resolveLostItemFee();

    final var response = checkInFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(item)
        .at(servicePointsFixture.cd1()));

    assertThat(response.getLoan(), nullValue());
    assertThat(loan, hasLostItemFee(isClosedCancelled(setCostFee)));
    assertThatPublishedLoanLogRecordEventsAreValid(loansClient.getById(loan.getId()).getJson());
  }

  @Test
  void lostFeeCancellationDoesNotTriggerMarkingItemAsLostAndPaid() {
    useChargeableRefundableLostItemFee(15.00, 0.0);

    declareItemLost();

    performActionThatRequiresRefund();
    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loan.getId());

    assertThat(itemsClient.getById(item.getId()).getJson(), isAvailable());
  }

  @Test
  void shouldCancelLostItemFeeActualCostAndProcessingFee() {
    final double itemFeeActualCost = 15.00;
    final double itemProcessingFee = 10.00;
    final UUID servicePointId = servicePointsFixture.cd1().getId();
    UserResource user = usersFixture.charlotte();
    feeFineOwnerFixture.ownerForServicePoint(servicePointId);

    var templateId = UUID.randomUUID();
    templateFixture.createDummyNoticeTemplate(templateId);

    use(PoliciesToActivate.builder()
      .lostItemPolicy(lostItemFeePoliciesFixture.create(
        lostItemFeePoliciesFixture.facultyStandardPolicy()
          .withName("Lost item fee actual cost policy")
          .chargeProcessingFeeWhenDeclaredLost(itemProcessingFee)
          .withActualCost(itemFeeActualCost)))
      .noticePolicy(noticePoliciesFixture.create(
        new NoticePolicyBuilder()
          .withName("Patron notice policy with fee/fine notices")
          .withFeeFineNotices(List.of(new NoticeConfigurationBuilder()
            .withAgedToLostReturnedEvent()
            .withTemplateId(templateId)
            .withUponAtTiming()
            .sendInRealTime(true)
            .create())))));

    loan = checkOutFixture.checkOutByBarcode(item, user);
    declareLostFixtures.declareItemLost(new DeclareItemLostRequestBuilder()
      .withServicePointId(servicePointId)
      .forLoanId(loan.getId()));
    assertThat(accountsClient.getAll(), hasSize(1));
    assertThat(actualCostRecordsClient.getAll(), hasSize(1));
    String recordId = actualCostRecordsClient.getAll().get(0).getString("id");
    runWithTimeOffset(() -> createLostItemFeeActualCostAccount(itemFeeActualCost,
      UUID.fromString(recordId), "AC info for staff", "AC info for patron"), ofMinutes(2));
    assertThat(accountsClient.getAll(), hasSize(2));

    checkInFixture.checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(item)
      .at(servicePointsFixture.cd1()));
    assertThat(loan, hasLostItemFeeActualCost(isClosedCancelled(itemFeeActualCost)));
    assertThat(loan, hasLostItemProcessingFee(isClosedCancelled(itemProcessingFee)));

    verifyNumberOfScheduledNotices(2);
    scheduledNoticeProcessingClient.runFeeFineNoticesProcessing(ZonedDateTime.now().plusHours(1));
    verifyNumberOfSentNotices(2);
    assertThat(FakeModNotify.getSentPatronNotices(), hasItems(
      hasEmailNoticeProperties(user.getId(), templateId, getFeeChargeAdditionalInfoContextMatcher(
        "AC info for patron"))));
  }
}
