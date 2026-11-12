package api.loans.anonymization;

import static api.support.APITestContext.getOkapiHeadersFromContext;
import static api.support.http.InterfaceUrls.circulationAnonymizeLoansInTenantURL;
import static api.support.matchers.LoanMatchers.isAnonymized;
import static org.folio.circulation.support.utils.ClockUtil.getZonedDateTime;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.builders.LoanHistoryConfigurationBuilder;
import api.support.fakes.FakeAnonymizationCandidatesResource;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import api.support.http.TimedTaskClient;
import io.vertx.core.MultiMap;

/**
 * Scheduled anonymization with user-specific loan anonymization, behavior tables on the page
 * "User settings snapshot for User-specific Loan Anonymization".
 */
class AnonymizeLoansByUserPreferenceTests extends LoanAnonymizationTests {
  private IndividualResource libraryPolicyUser;
  private IndividualResource asEarlyAsPossibleUser;
  private IndividualResource neverUser;
  private ItemResource item2;
  private ItemResource item3;

  @BeforeEach
  void createUsers() {
    FakeAnonymizationCandidatesResource.reset();
    libraryPolicyUser = user;
    asEarlyAsPossibleUser = usersFixture.james();
    neverUser = usersFixture.rebecca();
    item2 = itemsFixture.basedUponNod();
    item3 = itemsFixture.basedUponTemeraire();
    FakeAnonymizationCandidatesResource.setPreference(
      asEarlyAsPossibleUser.getId().toString(), "asEarlyAsPossible");
    FakeAnonymizationCandidatesResource.setPreference(neverUser.getId().toString(), "never");
  }

  @AfterEach
  void resetPreferences() {
    FakeAnonymizationCandidatesResource.reset();
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = "disabled")
  void featureDisabledIgnoresPreferences(String userSpecific) {
    createConfiguration(new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeImmediately()
      .userSpecific(userSpecific));

    UUID neverLoan = closedLoan(item1, neverUser);

    anonymizeLoansInTenant();

    assertThat(loansStorageClient.getById(neverLoan).getJson(), isAnonymized());
    assertThat(FakeAnonymizationCandidatesResource.getRequests(), empty());
  }

  @Test
  void featureEnabledAppliesEachUsersPreference() {
    createConfiguration(new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeImmediately()
      .userSpecificAnonymizeImmediately());

    UUID libraryPolicyLoan = closedLoan(item1, libraryPolicyUser);
    UUID asEarlyAsPossibleLoan = closedLoan(item2, asEarlyAsPossibleUser);
    UUID neverLoan = closedLoan(item3, neverUser);

    anonymizeLoansInTenant();

    assertThat(loansStorageClient.getById(libraryPolicyLoan).getJson(), isAnonymized());
    assertThat(loansStorageClient.getById(asEarlyAsPossibleLoan).getJson(), isAnonymized());
    assertThat(loansStorageClient.getById(neverLoan).getJson(), not(isAnonymized()));
  }

  @Test
  void asEarlyAsPossibleIsAnonymizedWhenLibraryPolicyIsNever() {
    createConfiguration(new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeNever()
      .userSpecificAnonymizeImmediately());

    UUID libraryPolicyLoan = closedLoan(item1, libraryPolicyUser);
    UUID asEarlyAsPossibleLoan = closedLoan(item2, asEarlyAsPossibleUser);
    UUID neverLoan = closedLoan(item3, neverUser);

    anonymizeLoansInTenant();

    assertThat(loansStorageClient.getById(libraryPolicyLoan).getJson(), not(isAnonymized()));
    assertThat(loansStorageClient.getById(asEarlyAsPossibleLoan).getJson(), isAnonymized());
    assertThat(loansStorageClient.getById(neverLoan).getJson(), not(isAnonymized()));
    assertThat(requestedPreferences(), contains("asEarlyAsPossible"));
  }

  @Test
  void eachGroupUsesItsOwnInterval() {
    createConfiguration(new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeAfterXInterval(1, "Weeks")
      .userSpecificAnonymizeAfterXInterval(1, "Minutes"));

    UUID libraryPolicyLoan = closedLoan(item1, libraryPolicyUser);
    UUID asEarlyAsPossibleLoan = closedLoan(item2, asEarlyAsPossibleUser);
    UUID neverLoan = closedLoan(item3, neverUser);

    mockClockManagerToReturnFixedDateTime(getZonedDateTime()
      .plus(ONE_MINUTE_AND_ONE_MILLIS, ChronoUnit.MILLIS));

    anonymizeLoansInTenant();

    assertThat(loansStorageClient.getById(libraryPolicyLoan).getJson(), not(isAnonymized()));
    assertThat(loansStorageClient.getById(asEarlyAsPossibleLoan).getJson(), isAnonymized());
    assertThat(loansStorageClient.getById(neverLoan).getJson(), not(isAnonymized()));
    assertThat(requestedPreferences(), contains("asEarlyAsPossible", "libraryPolicy"));
    FakeAnonymizationCandidatesResource.getRequests().forEach(request ->
      assertThat(request.get("returnedBefore"), notNullValue()));
  }

  @Test
  void libraryPolicyGetsTheLoansNotUsedByAsEarlyAsPossible() {
    createConfiguration(new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeImmediately()
      .userSpecificAnonymizeImmediately());

    closedLoan(item1, asEarlyAsPossibleUser);
    closedLoan(item2, asEarlyAsPossibleUser);

    anonymizeLoansInTenant();

    List<MultiMap> requests = FakeAnonymizationCandidatesResource.getRequests();
    assertThat(requests.get(0).get("limit"), is("25000"));
    assertThat(requests.get(1).get("limit"), is("49998"));
  }

  /**
   * Fees/fines treated differently: the fee/fine rule is shared, for these loans the preference
   * only matters when it is "never".
   */
  @Test
  void feeFineRuleAppliesToAllButNeverUsers() {
    createConfiguration(new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeNever()
      .feeFineCloseAnonymizeImmediately()
      .userSpecificAnonymizeAfterXInterval(1, "Weeks"));

    UUID libraryPolicyLoan = closedLoanWithClosedFee(item1, libraryPolicyUser);
    UUID asEarlyAsPossibleLoan = closedLoanWithClosedFee(item2, asEarlyAsPossibleUser);
    UUID neverLoan = closedLoanWithClosedFee(item3, neverUser);

    anonymizeLoansInTenant();

    assertThat(loansStorageClient.getById(libraryPolicyLoan).getJson(), isAnonymized());
    assertThat(loansStorageClient.getById(asEarlyAsPossibleLoan).getJson(), isAnonymized());
    assertThat(loansStorageClient.getById(neverLoan).getJson(), not(isAnonymized()));
    FakeAnonymizationCandidatesResource.getRequests().forEach(request ->
      assertThat(request.get("returnedBefore"), nullValue()));
  }

  @Test
  void unknownSettingKeepsAsEarlyAsPossibleLoans() {
    createConfiguration(new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeImmediately()
      .userSpecific("sometimes"));

    UUID libraryPolicyLoan = closedLoan(item1, libraryPolicyUser);
    UUID asEarlyAsPossibleLoan = closedLoan(item2, asEarlyAsPossibleUser);
    UUID neverLoan = closedLoan(item3, neverUser);

    anonymizeLoansInTenant();

    assertThat(loansStorageClient.getById(libraryPolicyLoan).getJson(), isAnonymized());
    assertThat(loansStorageClient.getById(asEarlyAsPossibleLoan).getJson(), not(isAnonymized()));
    assertThat(loansStorageClient.getById(neverLoan).getJson(), not(isAnonymized()));
  }

  /**
   * ui-circulation before UICIRC-1361 drops the top-level userSpecific on save. The
   * "as early as possible" run fails like a library interval without a period, the library
   * policy still runs with all loans to check.
   */
  @Test
  void intervalWithoutPeriodFailsOnlyAsEarlyAsPossible() {
    createConfiguration(new LoanHistoryConfigurationBuilder()
      .loanCloseAnonymizeImmediately()
      .userSpecific("interval"));

    UUID libraryPolicyLoan = closedLoan(item1, libraryPolicyUser);
    UUID asEarlyAsPossibleLoan = closedLoan(item2, asEarlyAsPossibleUser);
    UUID neverLoan = closedLoan(item3, neverUser);

    new TimedTaskClient(getOkapiHeadersFromContext())
      .start(circulationAnonymizeLoansInTenantURL(), 500, "anonymize-loans");

    assertThat(loansStorageClient.getById(libraryPolicyLoan).getJson(), isAnonymized());
    assertThat(loansStorageClient.getById(asEarlyAsPossibleLoan).getJson(), not(isAnonymized()));
    assertThat(loansStorageClient.getById(neverLoan).getJson(), not(isAnonymized()));
    assertThat(FakeAnonymizationCandidatesResource.getRequests().get(1).get("limit"),
      is("50000"));
  }

  private UUID closedLoan(ItemResource item, IndividualResource borrower) {
    UUID loanId = checkOut(item, borrower).getId();
    checkInFixture.checkInByBarcode(item);
    return loanId;
  }

  private UUID closedLoanWithClosedFee(ItemResource item, IndividualResource borrower) {
    IndividualResource loan = checkOut(item, borrower);
    createClosedAccountWithFeeFines(loan, getZonedDateTime());
    checkInFixture.checkInByBarcode(item);
    return loan.getId();
  }

  private IndividualResource checkOut(ItemResource item, IndividualResource borrower) {
    return checkOutFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder()
      .forItem(item)
      .to(borrower)
      .at(servicePoint.getId()));
  }

  private static List<String> requestedPreferences() {
    return FakeAnonymizationCandidatesResource.getRequests().stream()
      .map(request -> request.get("preference"))
      .toList();
  }
}
