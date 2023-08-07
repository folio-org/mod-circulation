package api.loans;

import api.support.APITests;
import api.support.MultipleJsonRecords;
import api.support.builders.DeclareItemLostRequestBuilder;
import api.support.builders.LostItemFeePolicyBuilder;
import api.support.http.*;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.LoanStatus;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.utils.ClockUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.Random;
import java.util.UUID;

import static api.support.APITestContext.getOkapiHeadersFromContext;
import static api.support.http.InterfaceUrls.scheduledActualCostExpiration;
import static api.support.matchers.ActualCostRecordMatchers.isInStatus;
import static api.support.matchers.LoanMatchers.hasStatus;
import static org.folio.HttpStatus.HTTP_NO_CONTENT;
import static org.folio.circulation.domain.ActualCostRecord.Status.OPEN;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNull;

class ActualCostRecordsWithNoExpirationDateTests extends APITests {
  private static final Period ITEM_AGED_TO_LOST_AFTER_OVERDUE = Period.minutes(1);
  private static final Double LOST_ITEM_PROCESSING_FEE = 25.0;
  private final TimedTaskClient timedTaskClient =  new TimedTaskClient(getOkapiHeadersFromContext());

  @BeforeEach
  public void beforeEach() {
    IndividualResource lostItemFeePolicy = lostItemFeePoliciesFixture.create(
      new LostItemFeePolicyBuilder()
        .withName("Lost item fee policy with no expiration period")
        .withLostItemProcessingFee(LOST_ITEM_PROCESSING_FEE)
        .withChargeAmountItemPatron(true)
        .withChargeAmountItemSystem(true)
        .withItemAgedToLostAfterOverdue(ITEM_AGED_TO_LOST_AFTER_OVERDUE)
        .doNotChargeProcessingFeeWhenDeclaredLost()
        .withActualCost(0.0));

    useLostItemPolicy(lostItemFeePolicy.getId());
    feeFineOwnerFixture.cd1Owner();
  }

  @Test
  void actualCostRecordIsCreatedWithNoExpirationDate() {
    JsonObject actualCostRecord = generateActualCostRecord();

    UUID recordId = UUID.fromString(actualCostRecord.getString("id"));
    assertNull(actualCostRecordsClient.get(recordId).getJson().getString("expirationDate"));
  }

  @Test
  void actualCostRecordWithNoExpirationDateIsSkipped() {
    JsonObject actualCostRecord = generateActualCostRecord();
    runProcessingAfterExpirationDate();

    assertThatActualCostRecordIsOpen(actualCostRecord);
    assertThatLoanIsOpenAndLost(actualCostRecord);
  }

  private void assertThatActualCostRecordIsOpen(JsonObject actualCostRecord) {

    UUID recordId = UUID.fromString(actualCostRecord.getString("id"));
    assertThat(actualCostRecordsClient.get(recordId).getJson(), is(isInStatus(OPEN)));
  }

  private JsonObject generateActualCostRecord() {
    ItemResource item = itemsFixture.basedUponSmallAngryPlanet(String.valueOf(new Random().nextInt()));
    CheckOutResource loan = checkOutFixture.checkOutByBarcode(item,
      usersFixture.jessica());

    declareLostFixtures.declareItemLost(new DeclareItemLostRequestBuilder()
      .withServicePointId(servicePointsFixture.cd1().getId())
      .forLoanId(loan.getId()));

    assertThatLoanIsOpenAndLost(loan.getId());

    MultipleJsonRecords actualCostRecords = actualCostRecordsClient.getMany(
      CqlQuery.exactMatch("loan.id", loan.getId().toString()));
    JsonObject record = actualCostRecords.getFirst();

    assertThat(actualCostRecords.size(), is(1));
    assertThat(record, is(isInStatus(OPEN)));

    return record;
  }

  private void assertThatLoanIsOpenAndLost(JsonObject actualCostRecord) {
    assertThatLoanIsOpenAndLost(getLoanId(actualCostRecord));
  }

  private void assertThatLoanIsOpenAndLost(UUID loanId) {
    JsonObject loan = loansFixture.getLoanById(loanId).getJson();
    UUID itemId = UUID.fromString(loan.getString("itemId"));

    assertThat(loan, hasStatus(LoanStatus.OPEN.getValue()));
    assertThat(itemsClient.getById(itemId).getJson(), hasStatus(ItemStatus.DECLARED_LOST.getValue()));
  }

  private static UUID getLoanId(JsonObject actualCostRecord) {
    return UUID.fromString(actualCostRecord.getJsonObject("loan").getString("id"));
  }

  private void runProcessingAfterExpirationDate() {
    runProcessing(ClockUtil.getZonedDateTime().plusMinutes(2));
  }

  private void runProcessing(ZonedDateTime processingTime) {
    mockClockManagerToReturnFixedDateTime(processingTime);
    timedTaskClient.start(scheduledActualCostExpiration(), HTTP_NO_CONTENT.toInt(),
      "scheduled-actual-cost-expiration");
    mockClockManagerToReturnDefaultDateTime();
  }

}
