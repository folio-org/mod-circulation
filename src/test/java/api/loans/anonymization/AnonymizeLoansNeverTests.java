package api.loans.anonymization;

import static api.support.matchers.LoanMatchers.isAnonymized;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;

import java.util.UUID;

import org.folio.circulation.support.utils.ClockUtil;
import org.junit.jupiter.api.Test;

import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.builders.LoanHistoryConfigurationBuilder;
import api.support.http.IndividualResource;

class AnonymizeLoansNeverTests extends LoanAnonymizationTests {

  /**
   * Scenario 1
   *
   *     Given:
   *         An Anonymize closed loans setting of "Never"
   *         An Anonymize closed loans with associated fees/fines setting of
   *         "Immediately after fee/fine closes"
   *         An open loan with an associated fee/fine
   *     When the item in the loan is checked in
   *     Then do not anonymize the loan
  */
  @Test
  void testClosedLoansWithFeesAndFinesNotAnonymized() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeNever()
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
   * Scenario 2
   *
   *     Given:
   *         An Anonymize closed loans setting of "Never"
   *         An Anonymize closed loans with associated fees/fines setting of
   *         "Immediately after fee/fine closes"
   *         A closed loan with an associated fee/fine
   *     When all fees/fines associated with the loan are closed
   *     Then anonymize the loan
   */
  @Test
  void testClosedLoansWithFeesAndFinesAnonymizedWhenFeesFinesClose() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
        .loanCloseAnonymizeNever()
        .feeFineCloseAnonymizeImmediately();
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = checkOutFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
        .to(user)
        .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createClosedAccountWithFeeFines(loanResource, ClockUtil.getDateTime().minusMinutes(1));
    checkInFixture.checkInByBarcode(item1);

    anonymizeLoansInTenant();

    assertThat(loansStorageClient.getById(loanID)
        .getJson(), isAnonymized());
  }

  /**
   * Scenario 3
   *
   *     Given:
   *         An Anonymize closed loans setting of "Never"
   *         An Anonymize closed loans with associated fees/fines setting of
   *         "Never"
   *         An open loan with an associated fee/fine
   *     When the item in the loan is checked in
   *     Then do not anonymize the loan
   */
  @Test
  void testClosedLoansWithFeesAndFinesNeverAnonymized() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
        .loanCloseAnonymizeNever()
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
   * Scenario 4
   *
   *     Given:
   *         An Anonymize closed loans setting of "Never"
   *         An Anonymize closed loans with associated fees/fines setting of
   *         "Never"
   *         A closed loan with an associated fee/fine
   *     When all fees/fines associated with the loan are closed
   *     Then do not anonymize the loan
   */
  @Test
  void testClosedLoanWithFeesAndFinesNeverAnonymizedWhenFeesFinesClose() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
        .loanCloseAnonymizeNever()
        .feeFineCloseAnonymizeNever();
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = checkOutFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
        .to(user)
        .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createClosedAccountWithFeeFines(loanResource, ClockUtil.getDateTime().minusMinutes(1));
    checkInFixture.checkInByBarcode(item1);

    anonymizeLoansInTenant();

    assertThat(loansStorageClient.getById(loanID).getJson(),
        not(isAnonymized()));
  }

  /**
   * Scenario 5
   *
   *     Given:
   *         An Anonymize closed loans setting of "Never"
   *         An Anonymize closed loans with associated fees/fines setting of
   *         "Never"
   *         An open loan with an associated fee/fine
   *     When the item in the loan is checked in
   *     Then do not anonymize the loan
   */
  @Test
  void testOpenLoanWithFeesAndFinesNeverAnonymizedWhenFeesFinesClose() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
        .loanCloseAnonymizeNever()
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
   * Scenario 6
   *
   *     Given:
   *         An Anonymize closed loans setting of "Never"
   *         An Anonymize closed loans with associated fees/fines setting of
   *         "Never"
   *         A closed loan with an associated fee/fine
   *     When all fees/fines associated with the loan are closed
   *     Then do not anonymize the loan
 */
  @Test
  void testClosedLoanWithClosedFeesAndFinesNeverAnonymizedWhenFeesFinesClose() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
        .loanCloseAnonymizeNever()
        .feeFineCloseAnonymizeNever();
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = checkOutFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
        .to(user)
        .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createClosedAccountWithFeeFines(loanResource, ClockUtil.getDateTime().minusMinutes(1));
    checkInFixture.checkInByBarcode(item1);

    anonymizeLoansInTenant();

    assertThat(loansStorageClient.getById(loanID).getJson(),
        not(isAnonymized()));
  }

  /**
   * Scenario 7
   *
   *     Given:
   *         An Anonymize closed loans setting of "Never"
   *         An Anonymize closed loans with associated fees/fines setting of
   *         "X interval after fee/fine closes"
   *         An open loan with an associated fee/fine
   *     When the item in the loan is checked in
   *     Then do not anonymize the loan
   */
  @Test
  void testClosedLoanWithFeesAndFinesNeverAnonymizedWhenXIntervalNotPassed() {

    LoanHistoryConfigurationBuilder loanHistoryConfig =
        new LoanHistoryConfigurationBuilder()
        .loanCloseAnonymizeNever()
        .feeFineCloseAnonymizeAfterXInterval(20, "minute");
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
   * Scenario 8
   *
   *     Given:
   *         An Anonymize closed loans setting of "Never"
   *         An Anonymize closed loans with associated fees/fines setting of
   *         "X interval after fee/fine closes"
   *         A closed loan with an associated fee/fine
   *     When all fees/fines associated with the loan are closed, and X
   *     interval after the fee/fine closes has passed
   *     Then anonymize the loan
   */
  @Test
  void testClosedLoanWithFeesAndFinesNeverAnonymizedWhenXIntervalPassed() {

    LoanHistoryConfigurationBuilder loanHistoryConfig =
        new LoanHistoryConfigurationBuilder()
            .loanCloseAnonymizeNever()
            .feeFineCloseAnonymizeAfterXInterval(20, "minute");
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = checkOutFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
        .to(user)
        .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createClosedAccountWithFeeFines(loanResource, ClockUtil.getDateTime().minusMinutes(1));
    checkInFixture.checkInByBarcode(item1);

    mockClockManagerToReturnFixedDateTime(
      ClockUtil.getDateTime().plus(20 * ONE_MINUTE_AND_ONE));

    anonymizeLoansInTenant();

    assertThat(loansStorageClient.getById(loanID).getJson(),
        isAnonymized());
  }

  @Test
  void testClosedLoansWithoutFeesAndFinesNeverAnonymized() {

    LoanHistoryConfigurationBuilder loanHistoryConfig =
        new LoanHistoryConfigurationBuilder()
        .loanCloseAnonymizeNever()
        .feeFineCloseAnonymizeImmediately();
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = checkOutFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
        .to(user)
        .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    checkInFixture.checkInByBarcode(item1);

    anonymizeLoansInTenant();

    assertThat(loansStorageClient.getById(loanID).getJson(),
        not(isAnonymized()));
  }


  @Test
  void testClosedLoansWithoutFeesAndFinesNeverAnonymized2() {

    LoanHistoryConfigurationBuilder loanHistoryConfig =
        new LoanHistoryConfigurationBuilder()
        .loanCloseAnonymizeNever()
        .feeFineCloseAnonymizeNever();
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = checkOutFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
        .to(user)
        .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    checkInFixture.checkInByBarcode(item1);

    anonymizeLoansInTenant();

    assertThat(loansStorageClient.getById(loanID).getJson(),
        not(isAnonymized()));
  }

  @Test
  void testClosedLoansWithoutFeesAndFinesNeverAnonymized3() {

    LoanHistoryConfigurationBuilder loanHistoryConfig =
        new LoanHistoryConfigurationBuilder()
        .loanCloseAnonymizeNever();
      createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = checkOutFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
        .to(user)
        .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    checkInFixture.checkInByBarcode(item1);

    anonymizeLoansInTenant();

    assertThat(loansStorageClient.getById(loanID).getJson(),
        not(isAnonymized()));
  }


}
