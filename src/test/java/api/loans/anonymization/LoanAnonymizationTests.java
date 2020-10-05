package api.loans.anonymization;

import static api.support.APITestContext.getOkapiHeadersFromContext;
import static api.support.http.InterfaceUrls.circulationAnonymizeLoansInTenantURL;
import static api.support.http.InterfaceUrls.circulationAnonymizeLoansURL;

import java.net.URL;
import java.util.UUID;

import api.support.fakes.FakePubSub;
import org.folio.circulation.domain.representations.anonymization.LoanAnonymizationAPIResponse;
import api.support.http.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;

import api.support.APITests;
import api.support.builders.AccountBuilder;
import api.support.builders.ConfigRecordBuilder;
import api.support.builders.FeefineActionsBuilder;
import api.support.builders.LoanHistoryConfigurationBuilder;
import api.support.http.ItemResource;
import api.support.http.TimedTaskClient;

abstract class LoanAnonymizationTests extends APITests {
  protected static final int ONE_MINUTE_AND_ONE = 60001;
  protected ItemResource item1;
  protected IndividualResource user;
  protected IndividualResource servicePoint;

  @Override
  public void beforeEach() throws InterruptedException {
    super.beforeEach();

    item1 = itemsFixture.basedUponSmallAngryPlanet();
    user = usersFixture.charlotte();
    servicePoint = servicePointsFixture.cd1();
  }

  @Override
  public void afterEach() {
    super.afterEach();

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
      .forAccount(account.getId())
      .withBalance(150)
      .withDate(null);

    feeFineActionsClient.create(builder);
  }

  void createClosedAccountWithFeeFines(IndividualResource loanResource,
    DateTime closedDate) {

    IndividualResource account = accountsClient.create(new AccountBuilder()
      .withLoan(loanResource)
      .feeFineStatusClosed()
      .withRemainingFeeFine(0));

    FeefineActionsBuilder builder = new FeefineActionsBuilder()
      .forAccount(account.getId())
      .withBalance(0)
      .withDate(closedDate);

    FeefineActionsBuilder builder1 = new FeefineActionsBuilder()
      .forAccount(account.getId())
      .withBalance(0)
      .withDate(closedDate.minusDays(1));

    feeFineActionsClient.create(builder);
    feeFineActionsClient.create(builder1);
  }

  protected void createConfiguration(
    LoanHistoryConfigurationBuilder loanHistoryConfig) {

    ConfigRecordBuilder configRecordBuilder = new ConfigRecordBuilder(
      "LOAN_HISTORY", "loan_history", loanHistoryConfig.create()
      .encodePrettily());

    configClient.create(configRecordBuilder);
  }
}
