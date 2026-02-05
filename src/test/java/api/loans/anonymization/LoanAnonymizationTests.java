package api.loans.anonymization;

import static api.support.APITestContext.getOkapiHeadersFromContext;
import static api.support.http.InterfaceUrls.circulationAnonymizeLoansInTenantURL;
import static api.support.http.InterfaceUrls.circulationAnonymizeLoansURL;

import java.net.URL;
import java.time.ZonedDateTime;
import java.util.UUID;

import org.folio.circulation.domain.representations.anonymization.LoanAnonymizationAPIResponse;
import org.folio.circulation.support.http.client.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import api.support.APITests;
import api.support.builders.AccountBuilder;
import api.support.builders.FeefineActionsBuilder;
import api.support.builders.LoanHistoryConfigurationBuilder;
import api.support.fakes.FakePubSub;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import api.support.http.TimedTaskClient;

abstract public class LoanAnonymizationTests extends APITests {
  protected static final int ONE_MINUTE_AND_ONE_MILLIS = 60001;
  protected ItemResource item1;
  protected IndividualResource user;
  protected IndividualResource servicePoint;

  @BeforeEach
  public void beforeEach() {
    item1 = itemsFixture.basedUponSmallAngryPlanet();
    user = usersFixture.charlotte();
    servicePoint = servicePointsFixture.cd1();
  }

  @AfterEach
  public void afterEach() {
    mockClockManagerToReturnDefaultDateTime();
    FakePubSub.clearPublishedEvents();
  }

  LoanAnonymizationAPIResponse anonymizeLoansInTenant() {
    return anonymizeLoans(circulationAnonymizeLoansInTenantURL())
      .getJson().mapTo(LoanAnonymizationAPIResponse.class);
  }

  void anonymizeLoansForUser(UUID userId) {
    anonymizeLoans(circulationAnonymizeLoansURL(userId.toString()));
  }

  private Response anonymizeLoans(URL url) {
    final TimedTaskClient timedTaskClient = new TimedTaskClient(
      getOkapiHeadersFromContext());

    return timedTaskClient.start(url, 200, "anonymize-loans");
  }

  void createOpenAccountWithFeeFines(IndividualResource loanResource) {
    IndividualResource account = accountsClient.create(new AccountBuilder()
      .feeFineStatusOpen()
      .withLoan(loanResource)
      .feeFineStatusOpen()
      .withRemainingFeeFine(150));

    FeefineActionsBuilder builder = new FeefineActionsBuilder()
      .withAccountId(account.getId())
      .withBalance(150.0)
      .withDateAction(null);

    feeFineActionsClient.create(builder);
  }

  void createClosedAccountWithFeeFines(IndividualResource loanResource,
    ZonedDateTime closedDate) {

    IndividualResource account = accountsClient.create(new AccountBuilder()
      .withLoan(loanResource)
      .feeFineStatusClosed()
      .withRemainingFeeFine(0));

    FeefineActionsBuilder builder = new FeefineActionsBuilder()
      .withAccountId(account.getId())
      .withBalance(0.0)
      .withDateAction(closedDate);

    FeefineActionsBuilder builder1 = new FeefineActionsBuilder()
      .withAccountId(account.getId())
      .withBalance(0.0)
      .withDateAction(closedDate.minusDays(1));

    feeFineActionsClient.create(builder);
    feeFineActionsClient.create(builder1);
  }

  protected void createConfiguration(LoanHistoryConfigurationBuilder loanHistoryConfig) {
    circulationSettingsFixture.createLoanHistorySettings(loanHistoryConfig);
  }
}
