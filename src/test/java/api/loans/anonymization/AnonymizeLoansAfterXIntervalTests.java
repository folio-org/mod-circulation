package api.loans.anonymization;

import static api.support.matchers.LoanMatchers.hasOpenStatus;
import static api.support.matchers.LoanMatchers.isAnonymized;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
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
  public void testClosedLoansWithFeesAndFinesNotAnonymizedAfterIntervalNotPassed()
      throws InterruptedException, ExecutionException, TimeoutException, MalformedURLException {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeAfterXInterval(1, "minute")
      .feeFineCloseAnonymizeImmediately();
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = loansFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createClosedAccountWithFeeFines(loanResource, DateTime.now().minusMinutes(1));

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
  public void testOpenLoansWithFeesAndFinesNotAnonymizedAfterIntervalNotPassed()
      throws InterruptedException, ExecutionException, TimeoutException, MalformedURLException {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeAfterXInterval(1, "minute")
      .feeFineCloseAnonymizeImmediately();
      createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = loansFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createClosedAccountWithFeeFines(loanResource, DateTime.now().minusMinutes(1));

    assertThat(loanResource.getJson(), hasOpenStatus());
    DateTimeUtils.setCurrentMillisOffset(ONE_MINUTE_AND_ONE);
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
  public void testClosedLoansWithClosedFeesAndFinesAnonymizedAfterIntervalPassed()
      throws InterruptedException, ExecutionException, TimeoutException, MalformedURLException {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeAfterXInterval(1, "minute")
      .feeFineCloseAnonymizeImmediately();
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = loansFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createClosedAccountWithFeeFines(loanResource, DateTime.now());

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
  public void testOpenLoansWithFeesAndFinesNotAnonymizedAfterFeeFineCloseIntervalNotPassed()
      throws InterruptedException, ExecutionException, TimeoutException, MalformedURLException {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeAfterXInterval(1, "minute")
      .feeFineCloseAnonymizeAfterXInterval(20, "minutes");
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = loansFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createClosedAccountWithFeeFines(loanResource, DateTime.now());

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
  public void testOpenLoansWithFeesAndFinesNotAnonymizedAfterFeeFineCloseIntervalPassed()
      throws InterruptedException, ExecutionException, TimeoutException, MalformedURLException {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeAfterXInterval(1, "minute")
      .feeFineCloseAnonymizeAfterXInterval(20, "minute");
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = loansFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createClosedAccountWithFeeFines(loanResource, DateTime.now());

    DateTimeUtils.setCurrentMillisOffset(20*ONE_MINUTE_AND_ONE);
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
  public void testClosedLoansWithClosedFeesAndFinesAnonymizedAfterFeeFineCloseIntervalPassed()
      throws InterruptedException, ExecutionException, TimeoutException, MalformedURLException {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeAfterXInterval(1, "minute")
      .feeFineCloseAnonymizeAfterXInterval(20, "minute");
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = loansFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createClosedAccountWithFeeFines(loanResource, DateTime.now());
    createClosedAccountWithFeeFines(loanResource, DateTime.now());

    loansFixture.checkInByBarcode(item1);

    DateTimeUtils.setCurrentMillisOffset(20 * ONE_MINUTE_AND_ONE);
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
  public void testNeverAnonymizeClosedLoansWithAssociatedFeeFines()
      throws InterruptedException, ExecutionException, TimeoutException, MalformedURLException {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeAfterXInterval(1, "minute")
      .feeFineCloseAnonymizeNever();
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = loansFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createClosedAccountWithFeeFines(loanResource, DateTime.now());

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
  public void testNeverAnonymizeClosedLoansWithAssociatedFeeFinesAfterAfterIntervalPassed()
      throws InterruptedException, ExecutionException, TimeoutException, MalformedURLException {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeAfterXInterval(1, "minute")
      .feeFineCloseAnonymizeNever();
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = loansFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createClosedAccountWithFeeFines(loanResource, DateTime.now());

    loansFixture.checkInByBarcode(item1);

    DateTimeUtils.setCurrentMillisOffset(ONE_MINUTE_AND_ONE);
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
  public void testNeverAnonymizeClosedLoansWithAssociatedFeeFinesAfterAfterIntervalNotPassed()
      throws InterruptedException, ExecutionException, TimeoutException, MalformedURLException {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeAfterXInterval(1, "minute")
      .feeFineCloseAnonymizeNever();
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = loansFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createClosedAccountWithFeeFines(loanResource, DateTime.now());

    loansFixture.checkInByBarcode(item1);

    anonymizeLoansInTenant();

    assertThat(loansStorageClient.getById(loanID)
      .getJson(), not(isAnonymized()));
  }

  @Test
  public void testClosedLoansAnonymizedAfterIntervalPassed()
      throws InterruptedException, ExecutionException, TimeoutException, MalformedURLException {

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

    DateTimeUtils.setCurrentMillisOffset(ONE_MINUTE_AND_ONE);
    anonymizeLoansInTenant();

    assertThat(loansStorageClient.getById(loanID)
        .getJson(), isAnonymized());
    assertThat(loansStorageClient.getById(loanResource2.getId())
        .getJson(), not(isAnonymized()));
  }

  @Test
  public void testClosedLoansNotAnonymizedAfterIntervalNotPassed()
      throws InterruptedException, ExecutionException, TimeoutException, MalformedURLException {

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
  public void testClosedLoanWithClosedFeesAndFinesNotAnonymizedIntervalNotPassed()
      throws InterruptedException, ExecutionException, TimeoutException, MalformedURLException {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder().loanCloseAnonymizeAfterXInterval(1,
        "minute");
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = loansFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
        .to(user)
        .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();


    createClosedAccountWithFeeFines(loanResource, DateTime.now());
    loansFixture.checkInByBarcode(item1);

    anonymizeLoansInTenant();

    assertThat(loansStorageClient.getById(loanID)
        .getJson(), not(isAnonymized()));
  }

  @Test
  public void testClosedLoanWithOpenFeesAndFinesNotAnonymizedIntervalPassed()
      throws InterruptedException, ExecutionException, TimeoutException, MalformedURLException {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder().loanCloseAnonymizeAfterXInterval(1,
        "minute");
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = loansFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
        .to(user)
        .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();


    createOpenAccountWithFeeFines(loanResource);
    DateTimeUtils.setCurrentMillisOffset(ONE_MINUTE_AND_ONE);
    loansFixture.checkInByBarcode(item1);

    anonymizeLoansInTenant();

    assertThat(loansStorageClient.getById(loanID)
        .getJson(), not(isAnonymized()));
  }

  @Test
  public void testNoConfigurationExceptionIsHandled()
    throws InterruptedException, ExecutionException, TimeoutException, MalformedURLException {

    IndividualResource loanResource = loansFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    Response response = anonymizeLoansInTenant();
    assertThat(response.getStatusCode(), is(422));
    assertThat(loansStorageClient.getById(loanID)
      .getJson(), not(isAnonymized()));
  }
}
