package api.loans.anonymization;

import static api.support.PubsubPublisherTestUtils.assertThatPublishedAnonymizeLoanLogRecordEventsAreValid;
import static api.support.fakes.PublishedEvents.byLogEventTypeAndAction;
import static api.support.matchers.LoanMatchers.isAnonymized;
import static api.support.matchers.LoanMatchers.isOpen;
import static org.folio.circulation.domain.representations.logs.LogEventType.LOAN;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import java.util.UUID;

import org.folio.circulation.domain.representations.anonymization.LoanAnonymizationAPIResponse;
import org.folio.circulation.support.utils.ClockUtil;
import org.joda.time.DateTime;
import org.junit.jupiter.api.Test;

import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.builders.LoanHistoryConfigurationBuilder;
import api.support.fakes.FakePubSub;
import api.support.fakes.PublishedEvents;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;

class AnonymizeLoansAfterXIntervalTests extends LoanAnonymizationTests {

  private DateTime lastAnonymizationDateTime = null;

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
  void testClosedLoansWithFeesAndFinesNotAnonymizedAfterIntervalNotPassed() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeAfterXInterval(1, "minute")
      .feeFineCloseAnonymizeImmediately();
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = checkOutFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createClosedAccountWithFeeFines(loanResource,
      ClockUtil.getDateTime().minusMinutes(1));

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
  void testOpenLoansWithFeesAndFinesNotAnonymizedAfterIntervalNotPassed() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeAfterXInterval(1, "minute")
      .feeFineCloseAnonymizeImmediately();
      createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = checkOutFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createClosedAccountWithFeeFines(loanResource,
      ClockUtil.getDateTime().minusMinutes(1));

    assertThat(loanResource.getJson(), isOpen());

    mockClockManagerToReturnFixedDateTime(
      ClockUtil.getDateTime().plus(ONE_MINUTE_AND_ONE));

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
  void testClosedLoansWithClosedFeesAndFinesAnonymizedAfterIntervalPassed() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeAfterXInterval(1, "minute")
      .feeFineCloseAnonymizeImmediately();
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = checkOutFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createClosedAccountWithFeeFines(loanResource, ClockUtil.getDateTime());

    checkInFixture.checkInByBarcode(item1);

    anonymizeLoansInTenant();

    assertThat(loansStorageClient.getById(loanID)
      .getJson(), isAnonymized());

    assertThatPublishedAnonymizeLoanLogRecordEventsAreValid(loansClient.getById(loanID).getJson());
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
  void testOpenLoansWithFeesAndFinesNotAnonymizedAfterFeeFineCloseIntervalNotPassed() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeAfterXInterval(1, "minute")
      .feeFineCloseAnonymizeAfterXInterval(20, "minutes");
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = checkOutFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createClosedAccountWithFeeFines(loanResource, ClockUtil.getDateTime());

    checkInFixture.checkInByBarcode(item1);

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
  void testOpenLoansWithFeesAndFinesNotAnonymizedAfterFeeFineCloseIntervalPassed() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeAfterXInterval(1, "minute")
      .feeFineCloseAnonymizeAfterXInterval(20, "minute");
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = checkOutFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createClosedAccountWithFeeFines(loanResource, ClockUtil.getDateTime());

    mockClockManagerToReturnFixedDateTime(
      ClockUtil.getDateTime().plus(20 * ONE_MINUTE_AND_ONE));
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
  void testClosedLoansWithClosedFeesAndFinesAnonymizedAfterFeeFineCloseIntervalPassed() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeAfterXInterval(1, "minute")
      .feeFineCloseAnonymizeAfterXInterval(20, "minute");
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = checkOutFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createClosedAccountWithFeeFines(loanResource, ClockUtil.getDateTime());
    createClosedAccountWithFeeFines(loanResource, ClockUtil.getDateTime());

    checkInFixture.checkInByBarcode(item1);

    mockClockManagerToReturnFixedDateTime(
      ClockUtil.getDateTime().plus(20 * ONE_MINUTE_AND_ONE));

    anonymizeLoansInTenant();

    assertThat(loansStorageClient.getById(loanID)
      .getJson(), isAnonymized());

    assertThatPublishedAnonymizeLoanLogRecordEventsAreValid(loansClient.getById(loanID).getJson());
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
  void testNeverAnonymizeClosedLoansWithAssociatedFeeFines() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeAfterXInterval(1, "minute")
      .feeFineCloseAnonymizeNever();
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = checkOutFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createClosedAccountWithFeeFines(loanResource, ClockUtil.getDateTime());

    checkInFixture.checkInByBarcode(item1);

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
  void testNeverAnonymizeClosedLoansWithAssociatedFeeFinesAfterAfterIntervalPassed() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeAfterXInterval(1, "minute")
      .feeFineCloseAnonymizeNever();
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = checkOutFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createClosedAccountWithFeeFines(loanResource, ClockUtil.getDateTime());

    checkInFixture.checkInByBarcode(item1);

    mockClockManagerToReturnFixedDateTime(ClockUtil.getDateTime().plus(ONE_MINUTE_AND_ONE));

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
  void testNeverAnonymizeClosedLoansWithAssociatedFeeFinesAfterAfterIntervalNotPassed() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeAfterXInterval(1, "minute")
      .feeFineCloseAnonymizeNever();
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = checkOutFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
      .to(user)
      .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    createClosedAccountWithFeeFines(loanResource, ClockUtil.getDateTime());

    checkInFixture.checkInByBarcode(item1);

    anonymizeLoansInTenant();

    assertThat(loansStorageClient.getById(loanID)
      .getJson(), not(isAnonymized()));
  }

  @Test
  void testClosedLoansAnonymizedAfterIntervalPassed() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder().loanCloseAnonymizeAfterXInterval(1,
        "minute");
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource1 = checkOutFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
        .to(user)
        .at(servicePoint.getId()));
    IndividualResource loanResource2 = checkOutFixture
        .checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(itemsFixture.basedUponNod())
            .to(usersFixture.rebecca())
            .at(servicePoint.getId()));

    UUID loanID = loanResource1.getId();

    checkInFixture.checkInByBarcode(item1);

    mockClockManagerToReturnFixedDateTime(ClockUtil.getDateTime().plus(ONE_MINUTE_AND_ONE));
    anonymizeLoansInTenant();

    assertThat(loansStorageClient.getById(loanID)
        .getJson(), isAnonymized());
    assertThat(loansStorageClient.getById(loanResource2.getId())
        .getJson(), not(isAnonymized()));
  }

  @Test
  void doNotAnonymizeLoansTwice() {
    createAnonymizeAfterIntervalConfiguration(1, "minute");

    final ItemResource nod = itemsFixture.basedUponNod();
    final ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    final IndividualResource firstLoan = checkOutFixture.checkOutByBarcode(smallAngryPlanet, user);
    final IndividualResource secondLoan = checkOutFixture.checkOutByBarcode(nod, usersFixture.rebecca());

    checkInFixture.checkInByBarcode(smallAngryPlanet);
    checkInFixture.checkInByBarcode(nod);

    setNextAnonymizationDateTime(ONE_MINUTE_AND_ONE);

    LoanAnonymizationAPIResponse firstAnonymization = anonymizeLoansInTenant();
    assertThat(firstAnonymization.getAnonymizedLoans().size(), is(2));
    assertThat(loansStorageClient.getById(firstLoan.getId()).getJson(), isAnonymized());
    assertThat(loansStorageClient.getById(secondLoan.getId()).getJson(), isAnonymized());

    final var anonymizedLoanLogEvents = FakePubSub.getPublishedEventsAsList(
      PublishedEvents.byLogEventTypeAndAction(LOAN.value(), "Anonymize"));

    assertThat(anonymizedLoanLogEvents, hasSize(2));
    FakePubSub.clearPublishedEvents();

    setNextAnonymizationDateTime(ONE_MINUTE_AND_ONE);

    LoanAnonymizationAPIResponse secondAnonymization = anonymizeLoansInTenant();

    assertThat(secondAnonymization.getAnonymizedLoans().size(), is(0));
    assertThat(FakePubSub.getPublishedEventsAsList(byLogEventTypeAndAction(LOAN.value(), "Anonymize")), hasSize(0));
  }

  @Test
  void ignoresAlreadyAnonymizedLoans() {
    createAnonymizeAfterIntervalConfiguration(1, "minute");

    final ItemResource nod = itemsFixture.basedUponNod();
    final ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final ItemResource temeraire = itemsFixture.basedUponTemeraire();
    final ItemResource dunkirk = itemsFixture.basedUponDunkirk();

    final IndividualResource firstLoan = checkOutFixture.checkOutByBarcode(smallAngryPlanet, user);
    final IndividualResource secondLoan = checkOutFixture.checkOutByBarcode(nod, usersFixture.rebecca());
    final IndividualResource thirdLoan = checkOutFixture.checkOutByBarcode(temeraire, usersFixture.james());
    final IndividualResource fourthLoan = checkOutFixture.checkOutByBarcode(dunkirk, usersFixture.steve());

    checkInFixture.checkInByBarcode(smallAngryPlanet);
    checkInFixture.checkInByBarcode(nod);

    setNextAnonymizationDateTime(ONE_MINUTE_AND_ONE);

    LoanAnonymizationAPIResponse firstAnonymization = anonymizeLoansInTenant();
    assertThat(firstAnonymization.getAnonymizedLoans(), hasSize(2));
    assertThat(loansStorageClient.getById(firstLoan.getId()).getJson(), isAnonymized());
    assertThat(loansStorageClient.getById(secondLoan.getId()).getJson(), isAnonymized());

    checkInFixture.checkInByBarcode(temeraire);
    checkInFixture.checkInByBarcode(dunkirk);

    setNextAnonymizationDateTime(ONE_MINUTE_AND_ONE);

    LoanAnonymizationAPIResponse secondAnonymization = anonymizeLoansInTenant();
    assertThat(secondAnonymization.getAnonymizedLoans(), hasSize(2));
    assertThat(secondAnonymization.getAnonymizedLoans(), hasItems(
      thirdLoan.getId().toString(), fourthLoan.getId().toString()));
  }

  @Test
  void testClosedLoansNotAnonymizedAfterIntervalNotPassed() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder().loanCloseAnonymizeAfterXInterval(1,
        "minute");
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = checkOutFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
        .to(user)
        .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();

    checkInFixture.checkInByBarcode(item1);

    anonymizeLoansInTenant();

    assertThat(loansStorageClient.getById(loanID)
        .getJson(), not(isAnonymized()));
  }

  @Test
  void testClosedLoanWithClosedFeesAndFinesNotAnonymizedIntervalNotPassed() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder().loanCloseAnonymizeAfterXInterval(1,
        "minute");
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = checkOutFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
        .to(user)
        .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();


    createClosedAccountWithFeeFines(loanResource, ClockUtil.getDateTime());
    checkInFixture.checkInByBarcode(item1);

    anonymizeLoansInTenant();

    assertThat(loansStorageClient.getById(loanID)
        .getJson(), not(isAnonymized()));
  }

  @Test
  void testClosedLoanWithOpenFeesAndFinesNotAnonymizedIntervalPassed() {

    LoanHistoryConfigurationBuilder loanHistoryConfig = new LoanHistoryConfigurationBuilder().loanCloseAnonymizeAfterXInterval(1,
        "minute");
    createConfiguration(loanHistoryConfig);

    IndividualResource loanResource = checkOutFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder().forItem(item1)
        .to(user)
        .at(servicePoint.getId()));
    UUID loanID = loanResource.getId();


    createOpenAccountWithFeeFines(loanResource);

    mockClockManagerToReturnFixedDateTime(ClockUtil.getDateTime().plus(ONE_MINUTE_AND_ONE));

    checkInFixture.checkInByBarcode(item1);

    anonymizeLoansInTenant();

    assertThat(loansStorageClient.getById(loanID)
        .getJson(), not(isAnonymized()));
  }

  private void createAnonymizeAfterIntervalConfiguration(int duration, String intervalName) {
    createConfiguration(new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeAfterXInterval(duration, intervalName));
  }

  private void setNextAnonymizationDateTime(long anonymizationInterval) {
    lastAnonymizationDateTime = lastAnonymizationDateTime == null
      ? ClockUtil.getDateTime().plus(anonymizationInterval)
      : lastAnonymizationDateTime.plus(anonymizationInterval);

    mockClockManagerToReturnFixedDateTime(lastAnonymizationDateTime);
  }
}
