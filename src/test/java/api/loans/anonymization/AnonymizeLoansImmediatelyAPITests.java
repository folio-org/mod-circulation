package api.loans.anonymization;

import static api.support.PubsubPublisherTestUtils.assertThatPublishedAnonymizeLoanLogRecordEventsAreValid;
import static api.support.matchers.LoanMatchers.isAnonymized;
import static org.folio.circulation.support.utils.ClockUtil.getZonedDateTime;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;

import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.builders.LoanHistoryConfigurationBuilder;
import api.support.http.IndividualResource;

class AnonymizeLoansImmediatelyAPITests extends LoanAnonymizationTests {

  /**
   *     Given:
   *         An Anonymize closed loans setting of "Immediately after loan closes"
   *         An Anonymize closed loans with associated fees/fines setting of "Immediately after fee/fine closes"
   *         An open loan with an associated fee/fine
   *     When the item in the loan is checked in
   *     Then do not anonymize the loan
   */
  @Test
  void shouldNotAnonymizeClosedLoansWithOpenFeesAndFinesAndSettingsOfAnonymizeImmediately() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeImmediately()
      .feeFineCloseAnonymizeImmediately();
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = checkOutFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createOpenAccountWithFeeFines(loanResource);
    checkInFixture.checkInByBarcode(item1);

    anonymizeLoansInTenant();

    assertThat(loansStorageClient.getById(loanID)
      .getJson(), not(isAnonymized()));
  }

  /**
   *     Given:
   *         An Anonymize closed loans setting of "Immediately after loan closes"
   *         An Anonymize closed loans with associated fees/fines setting of "Immediately after fee/fine closes"
   *         A closed loan with an associated fee/fine
   *     When all fees/fines associated with the loan are closed
   *     Then anonymize the loan
   */
  @Test
  void shouldAnonymizeClosedLoansWhenFeesAndFinesCloseAndSettingsOfAnonymizeImmediately() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeImmediately()
      .feeFineCloseAnonymizeImmediately();
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = checkOutFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createClosedAccountWithFeeFines(loanResource, getZonedDateTime());

    checkInFixture.checkInByBarcode(item1);

    anonymizeLoansInTenant();

    assertThat(loansStorageClient.getById(loanID)
      .getJson(), isAnonymized());
    assertThatPublishedAnonymizeLoanLogRecordEventsAreValid(loansClient.getById(loanID).getJson());
  }

  /**
   *     Given:
   *         An Anonymize closed loans setting of "Immediately after loan closes"
   *         An Anonymize closed loans with associated fees/fines setting of "Never"
   *         An open loan with an associated fee/fine
   *     When the item in the loan is checked in
   *     Then do not anonymize the loan
   */
  @Test
  void shouldNotAnonymizeWhenLoansWithOpenFeesAndFinesCloseAndSettingsOfNeverAnonymizeLoansWithFeesAndFines() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeImmediately()
      .feeFineCloseAnonymizeNever();
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = checkOutFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createOpenAccountWithFeeFines(loanResource);

    checkInFixture.checkInByBarcode(item1);

    anonymizeLoansInTenant();

    assertThat(loansStorageClient.getById(loanID).getJson(),
      not(isAnonymized()));
  }

  /**
   *     Given:
   *         An Anonymize closed loans setting of "Immediately after loan closes"
   *         An Anonymize closed loans with associated fees/fines setting of "Never"
   *         An open loan with an associated fee/fine
   *     When all fees/fines associated with the loan are closed
   *     Then do not anonymize the loan
   */
  @Test
  void shouldNotAnonymizeOpenLoansWhenFeesAndFinesCloseAndSettingsOfNeverAnonymizeLoansWithFeesAndFines() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeImmediately()
      .feeFineCloseAnonymizeNever();
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = checkOutFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createClosedAccountWithFeeFines(loanResource, getZonedDateTime());

    anonymizeLoansInTenant();

    assertThat(loansStorageClient.getById(loanID).getJson(),
      not(isAnonymized()));
  }

  /**
   *     Given:
   *         An Anonymize closed loans setting of "Immediately"
   *         An Anonymize closed loans with associated fees/fines setting of "X interval after fee/fine closes"
   *         An open loan with an associated fee/fine
   *     When the item in the loan is checked in
   *     Then do not anonymize the loan
   */
  @Test
  void shouldNotAnonymizeClosedLoansWithClosedFeesAndFinesWhenAnonymizationIntervalForLoansWithFeesAndFinesHasNotPassed() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeImmediately()
      .feeFineCloseAnonymizeAfterXInterval(1, "minute");
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = checkOutFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createClosedAccountWithFeeFines(loanResource, getZonedDateTime());

    checkInFixture.checkInByBarcode(item1);

    anonymizeLoansInTenant();

    assertThat(loansStorageClient.getById(loanID).getJson(),
      not(isAnonymized()));
  }

  /**
   *
   *     Given:
   *         An Anonymize closed loans setting of "Immediately"
   *         An Anonymize closed loans with associated fees/fines setting of "X interval after fee/fine closes"
   *         A closed loan with an associated fee/fine
   *     When all fees/fines associated with the loan are closed, and X interval has elapsed after the fees/fines have closed
   *     Then anonymize the loan
   */
  @Test
  void shouldAnonymizeClosedLoansWhenFeesAndFinesCloseAndAnonymizationIntervalForLoansWithFeesAndFinesHasPassed() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeImmediately()
      .feeFineCloseAnonymizeAfterXInterval(1, "minute");
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = checkOutFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createClosedAccountWithFeeFines(loanResource, getZonedDateTime());

    checkInFixture.checkInByBarcode(item1);

    mockClockManagerToReturnFixedDateTime(
      getZonedDateTime().plus(ONE_MINUTE_AND_ONE_MILLIS, ChronoUnit.MILLIS));

    anonymizeLoansInTenant();

    assertThat(loansStorageClient.getById(loanID).getJson(),
      isAnonymized());
    assertThatPublishedAnonymizeLoanLogRecordEventsAreValid(loansClient.getById(loanID).getJson());
  }
}
