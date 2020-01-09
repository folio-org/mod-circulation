package api.loans.anonymization;

import static api.support.matchers.LoanMatchers.isAnonymized;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.util.UUID;

import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.builders.LoanHistoryConfigurationBuilder;

public class AnonymizeLoansImmediatelyAPITests extends LoanAnonymizationTests {

  /**
   *     Given:
   *         An Anonymize closed loans setting of "Immediately after loan closes"
   *         An Anonymize closed loans with associated fees/fines setting of "Immediately after fee/fine closes"
   *         An open loan with an associated fee/fine
   *     When the item in the loan is checked in
   *     Then do not anonymize the loan
   */
  @Test
  public void shouldNotAnonymizeClosedLoansWithOpenFeesAndFinesAndSettingsOfAnonymizeImmediately() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeImmediately()
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
   *     Given:
   *         An Anonymize closed loans setting of "Immediately after loan closes"
   *         An Anonymize closed loans with associated fees/fines setting of "Immediately after fee/fine closes"
   *         A closed loan with an associated fee/fine
   *     When all fees/fines associated with the loan are closed
   *     Then anonymize the loan
   */
  @Test
  public void shouldAnonymizeClosedLoansWhenFeesAndFinesCloseAndSettingsOfAnonymizeImmediately() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeImmediately()
      .feeFineCloseAnonymizeImmediately();
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = loansFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createClosedAccountWithFeeFines(loanResource,
      DateTime.now(DateTimeZone.UTC));

    loansFixture.checkInByBarcode(item1);

    anonymizeLoansInTenant();

    assertThat(loansStorageClient.getById(loanID)
      .getJson(), isAnonymized());
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
  public void shouldNotAnonymizeWhenLoansWithOpenFeesAndFinesCloseAndSettingsOfNeverAnonymizeLoansWithFeesAndFines() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeImmediately()
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
   *     Given:
   *         An Anonymize closed loans setting of "Immediately after loan closes"
   *         An Anonymize closed loans with associated fees/fines setting of "Never"
   *         An open loan with an associated fee/fine
   *     When all fees/fines associated with the loan are closed
   *     Then do not anonymize the loan
   */
  @Test
  public void shouldNotAnonymizeOpenLoansWhenFeesAndFinesCloseAndSettingsOfNeverAnonymizeLoansWithFeesAndFines() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeImmediately()
      .feeFineCloseAnonymizeNever();
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = loansFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createClosedAccountWithFeeFines(loanResource,
      DateTime.now(DateTimeZone.UTC));

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
  public void shouldNotAnonymizeClosedLoansWithClosedFeesAndFinesWhenAnonymizationIntervalForLoansWithFeesAndFinesHasNotPassed() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeImmediately()
      .feeFineCloseAnonymizeAfterXInterval(1, "minute");
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = loansFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createClosedAccountWithFeeFines(loanResource, DateTime.now(DateTimeZone.UTC));

    loansFixture.checkInByBarcode(item1);

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
  public void shouldAnonymizeClosedLoansWhenFeesAndFinesCloseAndAnonymizationIntervalForLoansWithFeesAndFinesHasPassed() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeImmediately()
      .feeFineCloseAnonymizeAfterXInterval(1, "minute");
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = loansFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createClosedAccountWithFeeFines(loanResource, DateTime.now(DateTimeZone.UTC));

    loansFixture.checkInByBarcode(item1);

    mockClockManagerToReturnFixedDateTime(
      DateTime.now(DateTimeZone.UTC).plus(ONE_MINUTE_AND_ONE));

    anonymizeLoansInTenant();

    assertThat(loansStorageClient.getById(loanID).getJson(),
      isAnonymized());
  }
}
