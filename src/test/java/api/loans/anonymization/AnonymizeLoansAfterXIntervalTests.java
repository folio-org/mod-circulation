package api.loans.anonymization;

import static api.support.matchers.LoanMatchers.hasOpenStatus;
import static api.support.matchers.LoanMatchers.isAnonymized;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.joda.time.DateTime.now;
import static org.joda.time.DateTimeZone.UTC;

import java.util.UUID;

import org.folio.circulation.support.http.client.IndividualResource;
import org.junit.Test;

import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.builders.LoanHistoryConfigurationBuilder;

public class AnonymizeLoansAfterXIntervalTests extends LoanAnonymizationTests {

  /**
   * Scenario 1
   *
   *     Given:
   *         An Anonymize closed loans setting of "X interval after loan closes"
   *         An Anonymize closed loans with associated fees/fines setting of
   *         "Immediately"
   *         An open loan with an associated fee/fine
   *     When the item in the loan is checked in
   *     Then do not anonymize the loan
  */
  @Test
  public void testClosedLoansWithFeesAndFinesNotAnonymizedAfterIntervalNotPassed() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeAfterXInterval(1, "minute")
      .feeFineCloseAnonymizeImmediately();
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = loansFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createClosedAccountWithFeeFines(loanResource,
      now(UTC).minusMinutes(1));

    anonymizeLoansInTenant();

    assertThat(loansStorageClient.getById(loanID)
      .getJson(), not(isAnonymized()));
  }

  /**
   * Scenario 2
   *
   *     Given:
   *         An Anonymize closed loans setting of "X interval after loan closes"
   *         An Anonymize closed loans with associated fees/fines setting of
   *         "Immediately"
   *         An open loan with an associated fee/fine
   *     When X interval has elapsed after the loan closed
   *     Then do not anonymize the loan
   */
  @Test
  public void testOpenLoansWithFeesAndFinesNotAnonymizedAfterIntervalNotPassed() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeAfterXInterval(1, "minute")
      .feeFineCloseAnonymizeImmediately();
      createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = loansFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createClosedAccountWithFeeFines(loanResource,
      now(UTC).minusMinutes(1));

    assertThat(loanResource.getJson(), hasOpenStatus());

    mockClockManagerToReturnFixedDateTime(
      now(UTC).plus(ONE_MINUTE_AND_ONE));

    anonymizeLoansInTenant();

    assertThat(loansStorageClient.getById(loanID)
      .getJson(), not(isAnonymized()));
  }

  /**
   * Scenario 3
   *
   *     Given:
   *         An Anonymize closed loans setting of "X interval after loan closes"
   *         An Anonymize closed loans with associated fees/fines setting of
   *         "Immediately"
   *         A closed loan with an associated fee/fine
   *     When all fees/fines associated with the loan are closed
   *     Then anonymize the loan
   */
  @Test
  public void testClosedLoansWithClosedFeesAndFinesAnonymizedAfterIntervalPassed() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeAfterXInterval(1, "minute")
      .feeFineCloseAnonymizeImmediately();
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = loansFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createClosedAccountWithFeeFines(loanResource, now());

    loansFixture.checkInByBarcode(item1);

    anonymizeLoansInTenant();

    assertThat(loansStorageClient.getById(loanID)
      .getJson(), isAnonymized());
  }

  /**
   * Scenario 4
   *
   *     Given:
   *         An Anonymize closed loans setting of "X interval after loan closes"
   *         An Anonymize closed loans with associated fees/fines setting of
   *         "X interval after fee/fine closes"
   *         An open loan with an associated fee/fine
   *     When the item in the loan is checked in
   *     Then do not anonymize the loan
   */
  @Test
  public void testOpenLoansWithFeesAndFinesNotAnonymizedAfterFeeFineCloseIntervalNotPassed() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeAfterXInterval(1, "minute")
      .feeFineCloseAnonymizeAfterXInterval(20, "minutes");
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = loansFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createClosedAccountWithFeeFines(loanResource, now(UTC));

    loansFixture.checkInByBarcode(item1);

    anonymizeLoansInTenant();

    assertThat(loansStorageClient.getById(loanID)
      .getJson(), not(isAnonymized()));
  }

  /**
   * Scenario 5
   *
   *     Given:
   *         An Anonymize closed loans setting of "X interval after loan closes"
   *         An Anonymize closed loans with associated fees/fines setting of
   *         "X interval after fee/fine closes"
   *         An open loan with an associated fee/fine
   *     When X interval has elapsed after the loan closed
   *     Then do not anonymize the loan
   */
  @Test
  public void testOpenLoansWithFeesAndFinesNotAnonymizedAfterFeeFineCloseIntervalPassed() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeAfterXInterval(1, "minute")
      .feeFineCloseAnonymizeAfterXInterval(20, "minute");
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = loansFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createClosedAccountWithFeeFines(loanResource, now(UTC));

    mockClockManagerToReturnFixedDateTime(
      now(UTC).plus(20 * ONE_MINUTE_AND_ONE));
    anonymizeLoansInTenant();

    assertThat(loansStorageClient.getById(loanID)
      .getJson(), not(isAnonymized()));
  }

  /**
   * Scenario 6
   *
   *     Given:
   *         An Anonymize closed loans setting of "X interval after loan closes"
   *         An Anonymize closed loans with associated fees/fines setting of
   *         "X interval after fee/fine closes"
   *         A closed loan with an associated fee/fine
   *     When all fees/fines associated with the loan are closed, and X
   *     interval after the fee/fine closes has passed
   *     Then anonymize the loan
   */
  @Test
  public void testClosedLoansWithClosedFeesAndFinesAnonymizedAfterFeeFineCloseIntervalPassed() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeAfterXInterval(1, "minute")
      .feeFineCloseAnonymizeAfterXInterval(20, "minute");
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = loansFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createClosedAccountWithFeeFines(loanResource, now());
    createClosedAccountWithFeeFines(loanResource, now());

    loansFixture.checkInByBarcode(item1);

    mockClockManagerToReturnFixedDateTime(
      now(UTC).plus(20 * ONE_MINUTE_AND_ONE));

    anonymizeLoansInTenant();

    assertThat(loansStorageClient.getById(loanID)
      .getJson(), isAnonymized());
  }

  /**
   * Scenario 7
   *
   *     Given:
   *         An Anonymize closed loans setting of "X interval after loan closes"
   *         An Anonymize closed loans with associated fees/fines setting of
   *         "Never"
   *         An open loan with an associated fee/fine
   *     When the item in the loan is checked in
   *     Then do not anonymize the loan
   */
  @Test
  public void testNeverAnonymizeClosedLoansWithAssociatedFeeFines() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeAfterXInterval(1, "minute")
      .feeFineCloseAnonymizeNever();
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = loansFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createClosedAccountWithFeeFines(loanResource, now(UTC));

    loansFixture.checkInByBarcode(item1);

    anonymizeLoansInTenant();

    assertThat(loansStorageClient.getById(loanID)
      .getJson(), not(isAnonymized()));
  }

  /**
   * Scenario 8
   *
   *     Given:
   *         An Anonymize closed loans setting of "X interval after loan closes"
   *         An Anonymize closed loans with associated fees/fines setting of
   *         "Never"
   *         An open loan with an associated fee/fine
   *     When X interval has elapsed after the loan closed
   *     Then do not anonymize the loan
   */
  @Test
  public void testNeverAnonymizeClosedLoansWithAssociatedFeeFinesAfterAfterIntervalPassed() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeAfterXInterval(1, "minute")
      .feeFineCloseAnonymizeNever();
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = loansFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createClosedAccountWithFeeFines(loanResource, now(UTC));

    loansFixture.checkInByBarcode(item1);

    mockClockManagerToReturnFixedDateTime(now(UTC).plus(ONE_MINUTE_AND_ONE));

    anonymizeLoansInTenant();

    assertThat(loansStorageClient.getById(loanID)
      .getJson(), not(isAnonymized()));
  }

  /**
   * Scenario 9
   *
   *     Given:
   *         An Anonymize closed loans setting of "X interval after loan closes"
   *         An Anonymize closed loans with associated fees/fines setting of
   *         "Never"
   *         A closed loan with an associated fee/fine
   *     When all fees/fines associated with the loan are closed
   *     Then do not anonymize the loan
   */
  @Test
  public void testNeverAnonymizeClosedLoansWithAssociatedFeeFinesAfterAfterIntervalNotPassed() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeAfterXInterval(1, "minute")
      .feeFineCloseAnonymizeNever();
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = loansFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createClosedAccountWithFeeFines(loanResource, now());

    loansFixture.checkInByBarcode(item1);

    anonymizeLoansInTenant();

    assertThat(loansStorageClient.getById(loanID)
      .getJson(), not(isAnonymized()));
  }

  @Test
  public void testClosedLoansAnonymizedAfterIntervalPassed() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder().loanCloseAnonymizeAfterXInterval(1,
        "minute");
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource1 = loansFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
        .to(user)
        .at(servicePoint.getId()));
    IndividualResource loanResource2 = loansFixture
        .checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(itemsFixture.basedUponNod())
            .to(usersFixture.rebecca())
            .at(servicePoint.getId()));

    UUID loanID = loanResource1.getId();

    loansFixture.checkInByBarcode(item1);

    mockClockManagerToReturnFixedDateTime(now(UTC).plus(ONE_MINUTE_AND_ONE));
    anonymizeLoansInTenant();

    assertThat(loansStorageClient.getById(loanID)
        .getJson(), isAnonymized());
    assertThat(loansStorageClient.getById(loanResource2.getId())
        .getJson(), not(isAnonymized()));
  }

  @Test
  public void testClosedLoansNotAnonymizedAfterIntervalNotPassed() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder().loanCloseAnonymizeAfterXInterval(1,
        "minute");
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = loansFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
        .to(user)
        .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    loansFixture.checkInByBarcode(item1);

    anonymizeLoansInTenant();

    assertThat(loansStorageClient.getById(loanID)
        .getJson(), not(isAnonymized()));
  }

  @Test
  public void testClosedLoanWithClosedFeesAndFinesNotAnonymizedIntervalNotPassed() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder().loanCloseAnonymizeAfterXInterval(1,
        "minute");
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = loansFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
        .to(user)
        .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();


    createClosedAccountWithFeeFines(loanResource, now());
    loansFixture.checkInByBarcode(item1);

    anonymizeLoansInTenant();

    assertThat(loansStorageClient.getById(loanID)
        .getJson(), not(isAnonymized()));
  }

  @Test
  public void testClosedLoanWithOpenFeesAndFinesNotAnonymizedIntervalPassed() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder().loanCloseAnonymizeAfterXInterval(1,
        "minute");
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = loansFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
        .to(user)
        .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();


    createOpenAccountWithFeeFines(loanResource);

    mockClockManagerToReturnFixedDateTime(now(UTC).plus(ONE_MINUTE_AND_ONE));

    loansFixture.checkInByBarcode(item1);

    anonymizeLoansInTenant();

    assertThat(loansStorageClient.getById(loanID)
        .getJson(), not(isAnonymized()));
  }
}
