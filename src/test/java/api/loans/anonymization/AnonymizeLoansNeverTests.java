package api.loans.anonymization;

import static api.support.matchers.LoanMatchers.isAnonymized;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.UUID;

import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.builders.LoanHistoryConfigurationBuilder;

public class AnonymizeLoansNeverTests extends LoanAnonymizationTests {

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
  public void testClosedLoansWithFeesAndFinesNotAnonymized() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeNever()
      .feeFineCloseAnonymizeImmediately();
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = loansFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createOpenAccountWithFeeFines(loanResource);
    loansFixture.checkInByBarcode(item1);

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
  public void testClosedLoansWithFeesAndFinesAnonymizedWhenFeesFinesClose() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
        .loanCloseAnonymizeNever()
        .feeFineCloseAnonymizeImmediately();
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = loansFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
        .to(user)
        .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createClosedAccountWithFeeFines(loanResource, DateTime.now().minusMinutes(1));
    loansFixture.checkInByBarcode(item1);

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
  public void testClosedLoansWithFeesAndFinesNeverAnonymized() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
        .loanCloseAnonymizeNever()
        .feeFineCloseAnonymizeNever();
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = loansFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
        .to(user)
        .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createOpenAccountWithFeeFines(loanResource);
    loansFixture.checkInByBarcode(item1);

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
  public void testClosedLoanWithFeesAndFinesNeverAnonymizedWhenFeesFinesClose() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
        .loanCloseAnonymizeNever()
        .feeFineCloseAnonymizeNever();
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = loansFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
        .to(user)
        .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createClosedAccountWithFeeFines(loanResource, DateTime.now().minusMinutes(1));
    loansFixture.checkInByBarcode(item1);

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
  public void testOpenLoanWithFeesAndFinesNeverAnonymizedWhenFeesFinesClose() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
        .loanCloseAnonymizeNever()
        .feeFineCloseAnonymizeNever();
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = loansFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
        .to(user)
        .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createOpenAccountWithFeeFines(loanResource);
    loansFixture.checkInByBarcode(item1);

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
  public void testClosedLoanWithClosedFeesAndFinesNeverAnonymizedWhenFeesFinesClose() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
        .loanCloseAnonymizeNever()
        .feeFineCloseAnonymizeNever();
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = loansFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
        .to(user)
        .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createClosedAccountWithFeeFines(loanResource, DateTime.now().minusMinutes(1));
    loansFixture.checkInByBarcode(item1);

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
  public void testClosedLoanWithFeesAndFinesNeverAnonymizedWhenXIntervalNotPassed() {

    LoanHistoryConfigurationBuilder loanHistoryConfig =
        new LoanHistoryConfigurationBuilder()
        .loanCloseAnonymizeNever()
        .feeFineCloseAnonymizeAfterXInterval(20, "minute");
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = loansFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
        .to(user)
        .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createOpenAccountWithFeeFines(loanResource);
    loansFixture.checkInByBarcode(item1);

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
  public void testClosedLoanWithFeesAndFinesNeverAnonymizedWhenXIntervalPassed() {

    LoanHistoryConfigurationBuilder loanHistoryConfig =
        new LoanHistoryConfigurationBuilder()
            .loanCloseAnonymizeNever()
            .feeFineCloseAnonymizeAfterXInterval(20, "minute");
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = loansFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
        .to(user)
        .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createClosedAccountWithFeeFines(loanResource, DateTime.now().minusMinutes(1));
    loansFixture.checkInByBarcode(item1);

    mockClockManagerToReturnFixedDateTime(
      DateTime.now(DateTimeZone.UTC).plus(20 * ONE_MINUTE_AND_ONE));

    anonymizeLoansInTenant();

    assertThat(loansStorageClient.getById(loanID).getJson(),
        isAnonymized());
  }

  @Test
  public void testClosedLoansWithoutFeesAndFinesNeverAnonymized() {

    LoanHistoryConfigurationBuilder loanHistoryConfig =
        new LoanHistoryConfigurationBuilder()
        .loanCloseAnonymizeNever()
        .feeFineCloseAnonymizeImmediately();
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = loansFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
        .to(user)
        .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    loansFixture.checkInByBarcode(item1);

    anonymizeLoansInTenant();

    assertThat(loansStorageClient.getById(loanID).getJson(),
        not(isAnonymized()));
  }


  @Test
  public void testClosedLoansWithoutFeesAndFinesNeverAnonymized2() {

    LoanHistoryConfigurationBuilder loanHistoryConfig =
        new LoanHistoryConfigurationBuilder()
        .loanCloseAnonymizeNever()
        .feeFineCloseAnonymizeNever();
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = loansFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
        .to(user)
        .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    loansFixture.checkInByBarcode(item1);

    anonymizeLoansInTenant();

    assertThat(loansStorageClient.getById(loanID).getJson(),
        not(isAnonymized()));
  }

  @Test
  public void testClosedLoansWithoutFeesAndFinesNeverAnonymized3() {

    LoanHistoryConfigurationBuilder loanHistoryConfig =
        new LoanHistoryConfigurationBuilder()
        .loanCloseAnonymizeNever();
      createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = loansFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
        .to(user)
        .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    loansFixture.checkInByBarcode(item1);

    anonymizeLoansInTenant();

    assertThat(loansStorageClient.getById(loanID).getJson(),
        not(isAnonymized()));
  }


}
