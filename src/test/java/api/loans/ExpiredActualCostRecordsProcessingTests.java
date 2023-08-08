package api.loans;

import static api.support.APITestContext.getOkapiHeadersFromContext;
import static api.support.http.InterfaceUrls.scheduledActualCostExpiration;
import static api.support.matchers.ActualCostRecordMatchers.isInStatus;
import static api.support.matchers.LoanMatchers.hasStatus;
import static org.folio.HttpStatus.HTTP_NO_CONTENT;
import static org.folio.circulation.domain.ActualCostRecord.Status.EXPIRED;
import static org.folio.circulation.domain.ActualCostRecord.Status.OPEN;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;

import java.time.ZonedDateTime;
import java.util.Random;
import java.util.UUID;

import org.folio.circulation.domain.ActualCostRecord;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.LoanStatus;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.utils.ClockUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import api.support.APITests;
import api.support.MultipleJsonRecords;
import api.support.builders.DeclareItemLostRequestBuilder;
import api.support.builders.LostItemFeePolicyBuilder;
import api.support.http.CheckOutResource;
import api.support.http.CqlQuery;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import api.support.http.TimedTaskClient;
import io.vertx.core.json.JsonObject;

class ExpiredActualCostRecordsProcessingTests extends APITests {
  private static final Period ACTUAL_COST_RECORD_EXPIRATION_PERIOD = Period.minutes(1);
  private final TimedTaskClient timedTaskClient =  new TimedTaskClient(getOkapiHeadersFromContext());

  @BeforeEach
  public void beforeEach() {
    IndividualResource lostItemFeePolicy = lostItemFeePoliciesFixture.create(
      new LostItemFeePolicyBuilder()
        .withName("Lost item fee policy with actual cost")
        .doNotChargeProcessingFeeWhenDeclaredLost()
        .withActualCost(0.0)
        .withLostItemChargeFeeFine(ACTUAL_COST_RECORD_EXPIRATION_PERIOD));

    useLostItemPolicy(lostItemFeePolicy.getId());
    feeFineOwnerFixture.cd1Owner();
  }

  @Test
  void openExpiredActualCostRecordIsProcessed() {
    JsonObject actualCostRecord = generateActualCostRecord(OPEN);
    runProcessingAfterExpirationDate();

    assertThatActualCostRecordIsInStatus(actualCostRecord, EXPIRED);
    assertThatLoanIsClosedAsLostAndPaid(actualCostRecord);
  }

  @Test
  void openActiveActualCostRecordIsSkipped() {
    JsonObject actualCostRecord = generateActualCostRecord(OPEN);
    runProcessingBeforeExpirationDate();

    assertThatActualCostRecordIsInStatus(actualCostRecord, OPEN);
    assertThatLoanIsOpenAndLost(actualCostRecord);
  }

  @Test
  void actualCostRecordWithNoExpirationDateIsSkipped() {
    useLostItemPolicy(lostItemFeePoliciesFixture.create(
      new LostItemFeePolicyBuilder()
        .withName("Lost item fee policy with no expiration period")
        .doNotChargeProcessingFeeWhenDeclaredLost()
        .withActualCost(0.0)
        .withLostItemChargeFeeFine(null)).getId());

    JsonObject actualCostRecord = generateActualCostRecord(OPEN);
    runProcessingAfterExpirationDate();

    assertThatActualCostRecordIsInStatus(actualCostRecord, OPEN);
    assertThatLoanIsOpenAndLost(actualCostRecord);
  }

  @ParameterizedTest
  @EnumSource(mode = EXCLUDE, names = {"OPEN"})
  void closedActiveActualCostRecordIsSkipped(ActualCostRecord.Status status) {
    JsonObject actualCostRecord = generateActualCostRecord(status);
    runProcessingBeforeExpirationDate();

    assertThatActualCostRecordIsInStatus(actualCostRecord, status);
    assertThatLoanIsOpenAndLost(actualCostRecord);
  }

  @ParameterizedTest
  @EnumSource(mode = EXCLUDE, names = {"OPEN"})
  void closedExpiredActualCostRecordIsSkipped(ActualCostRecord.Status status) {
    JsonObject actualCostRecord = generateActualCostRecord(status);
    runProcessingAfterExpirationDate();

    assertThatActualCostRecordIsInStatus(actualCostRecord, status);
    assertThatLoanIsOpenAndLost(actualCostRecord);
  }

  @Test
  void multipleOpenExpiredActualCostRecordsAreProcessed() {
    JsonObject firstRecord = generateActualCostRecord(OPEN);
    JsonObject secondRecord = generateActualCostRecord(OPEN);
    JsonObject thirdRecord = generateActualCostRecord(OPEN);

    runProcessingAfterExpirationDate();

    assertThatActualCostRecordIsInStatus(firstRecord, EXPIRED);
    assertThatActualCostRecordIsInStatus(secondRecord, EXPIRED);
    assertThatActualCostRecordIsInStatus(thirdRecord, EXPIRED);

    assertThatLoanIsClosedAsLostAndPaid(firstRecord);
    assertThatLoanIsClosedAsLostAndPaid(secondRecord);
    assertThatLoanIsClosedAsLostAndPaid(thirdRecord);
  }

  private void assertThatActualCostRecordIsInStatus(JsonObject actualCostRecord,
    ActualCostRecord.Status status) {

    UUID recordId = UUID.fromString(actualCostRecord.getString("id"));
    assertThat(actualCostRecordsClient.get(recordId).getJson(), is(isInStatus(status)));
  }

  private JsonObject generateActualCostRecord(ActualCostRecord.Status status) {
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

    if (status != OPEN) {
      record = record.put("status", status.getValue());
      actualCostRecordsClient.replace(UUID.fromString(record.getString("id")), record);
    }

    return record;
  }

  private void assertThatLoanIsClosedAsLostAndPaid(JsonObject actualCostRecord) {
    verifyLoanAndItemStatuses(getLoanId(actualCostRecord), LoanStatus.CLOSED, ItemStatus.LOST_AND_PAID);
  }

  private void assertThatLoanIsOpenAndLost(JsonObject actualCostRecord) {
    assertThatLoanIsOpenAndLost(getLoanId(actualCostRecord));
  }

  private void assertThatLoanIsOpenAndLost(UUID loanId) {
    verifyLoanAndItemStatuses(loanId, LoanStatus.OPEN, ItemStatus.DECLARED_LOST);
  }

  private void verifyLoanAndItemStatuses(UUID loanId, LoanStatus loanStatus, ItemStatus itemStatus) {
    JsonObject loan = loansFixture.getLoanById(loanId).getJson();
    UUID itemId = UUID.fromString(loan.getString("itemId"));

    assertThat(loan, hasStatus(loanStatus.getValue()));
    assertThat(itemsClient.getById(itemId).getJson(), hasStatus(itemStatus.getValue()));
  }

  private static UUID getLoanId(JsonObject actualCostRecord) {
    return UUID.fromString(actualCostRecord.getJsonObject("loan").getString("id"));
  }

  private void runProcessingBeforeExpirationDate() {
    runProcessing(ClockUtil.getZonedDateTime());
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
