package api.support;

import static api.support.APITestContext.deployVerticles;
import static api.support.APITestContext.getOkapiHeadersFromContext;
import static api.support.APITestContext.undeployVerticles;
import static api.support.fakes.LoanHistoryProcessor.setLoanHistoryEnabled;
import static api.support.http.ResourceClient.forLoanHistoryStorage;
import static api.support.http.ResourceClient.forTenantStorage;
import static org.folio.circulation.domain.representations.LoanProperties.PATRON_GROUP_AT_CHECKOUT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.ClockManager;
import api.support.http.IndividualResource;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;

import api.support.fakes.FakePubSub;
import api.support.fixtures.AddressTypesFixture;
import api.support.fixtures.AgeToLostFixture;
import api.support.fixtures.AutomatedPatronBlocksFixture;
import api.support.fixtures.CancellationReasonsFixture;
import api.support.fixtures.ChangeDueDateFixture;
import api.support.fixtures.CheckInFixture;
import api.support.fixtures.CheckOutFixture;
import api.support.fixtures.CirculationRulesFixture;
import api.support.fixtures.ClaimItemReturnedFixture;
import api.support.fixtures.DeclareLostFixtures;
import api.support.fixtures.EndPatronSessionClient;
import api.support.fixtures.EventSubscribersFixture;
import api.support.fixtures.ExpiredSessionProcessingClient;
import api.support.fixtures.FeeFineAccountFixture;
import api.support.fixtures.FeeFineOwnerFixture;
import api.support.fixtures.FeeFineTypeFixture;
import api.support.fixtures.HoldingsFixture;
import api.support.fixtures.IdentifierTypesFixture;
import api.support.fixtures.InstancesFixture;
import api.support.fixtures.ItemsFixture;
import api.support.fixtures.LoanPoliciesFixture;
import api.support.fixtures.LoanTypesFixture;
import api.support.fixtures.LoansFixture;
import api.support.fixtures.LocationsFixture;
import api.support.fixtures.LostItemFeePoliciesFixture;
import api.support.fixtures.MaterialTypesFixture;
import api.support.fixtures.NoteTypeFixture;
import api.support.fixtures.NoticePoliciesFixture;
import api.support.fixtures.OverdueFinePoliciesFixture;
import api.support.fixtures.PatronGroupsFixture;
import api.support.fixtures.ProxyRelationshipsFixture;
import api.support.fixtures.RequestPoliciesFixture;
import api.support.fixtures.RequestQueueFixture;
import api.support.fixtures.RequestsFixture;
import api.support.fixtures.ScheduledNoticeProcessingClient;
import api.support.fixtures.ServicePointsFixture;
import api.support.fixtures.TemplateFixture;
import api.support.fixtures.TenantActivationFixture;
import api.support.fixtures.UserManualBlocksFixture;
import api.support.fixtures.UsersFixture;
import api.support.fixtures.policies.PoliciesActivationFixture;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;
import lombok.experimental.Delegate;

public abstract class APITests {
  private static boolean okapiAlreadyDeployed = false;

  private final RestAssuredClient restAssuredClient = new RestAssuredClient(
    getOkapiHeadersFromContext());

  private final boolean initialiseCirculationRules;

  private final ResourceClient servicePointsClient = ResourceClient.forServicePoints();

  private final ResourceClient institutionsClient = ResourceClient.forInstitutions();
  private final ResourceClient campusesClient = ResourceClient.forCampuses();
  private final ResourceClient librariesClient = ResourceClient.forLibraries();
  protected final ResourceClient locationsClient = ResourceClient.forLocations();

  protected final ResourceClient configClient = ResourceClient.forConfiguration();

  private final ResourceClient patronGroupsClient
    = ResourceClient.forPatronGroups();

  protected final ResourceClient usersClient = ResourceClient.forUsers();
  private final ResourceClient proxyRelationshipsClient
    = ResourceClient.forProxyRelationships();

  protected final ResourceClient instancesClient = ResourceClient.forInstances();
  protected final ResourceClient holdingsClient = ResourceClient.forHoldings();
  protected final ResourceClient itemsClient = ResourceClient.forItems();

  protected final ResourceClient loansClient = ResourceClient.forLoans();
  protected final ResourceClient accountsClient = ResourceClient.forAccounts();
  protected final ResourceClient feeFineActionsClient = ResourceClient.forFeeFineActions();
  protected final ResourceClient feeFineOwnersClient = ResourceClient.forFeeFineOwners();
  protected final ResourceClient feeFinesClient = ResourceClient.forFeeFines();

  protected final ResourceClient loansStorageClient
    = ResourceClient.forLoansStorage();

  protected final ResourceClient requestsStorageClient
    = ResourceClient.forRequestsStorage();

  protected final ResourceClient requestsClient = ResourceClient.forRequests();

  protected final ResourceClient fixedDueDateScheduleClient
    = ResourceClient.forFixedDueDateSchedules();

  protected final ResourceClient loanPolicyClient
    = ResourceClient.forLoanPolicies();

  protected final ResourceClient requestPolicyClient
    = ResourceClient.forRequestPolicies();

  protected final ResourceClient notesClient
    = ResourceClient.forNotes();

  protected final ResourceClient noteTypeClient
    = ResourceClient.forNoteTypes();

  protected final ResourceClient noticePolicyClient
    = ResourceClient.forNoticePolicies();

  protected final ResourceClient overdueFinePolicyClient
    = ResourceClient.forOverdueFinePolicies();

  protected final ResourceClient lostItemFeePolicyClient
    = ResourceClient.forLostItemFeePolicies();

  protected final ResourceClient templateClient
    = ResourceClient.forTemplates();

  private final ResourceClient instanceTypesClient
    = ResourceClient.forInstanceTypes();

  private final ResourceClient contributorNameTypesClient
    = ResourceClient.forContributorNameTypes();

  protected final ResourceClient patronNoticesClient =
    ResourceClient.forPatronNotices();

  protected final ResourceClient scheduledNoticesClient =
    ResourceClient.forScheduledNotices();

  protected final ResourceClient patronSessionRecordsClient =
    ResourceClient.forPatronSessionRecords();

  protected final ResourceClient checkInOperationClient =
    ResourceClient.forCheckInStorage();

  protected final EndPatronSessionClient endPatronSessionClient =
    new EndPatronSessionClient();

  protected final ExpiredSessionProcessingClient expiredSessionProcessingClient =
    new ExpiredSessionProcessingClient();

  protected final ResourceClient expiredEndSessionClient =
    ResourceClient.forExpiredSessions();
  protected final ResourceClient loanHistoryClient = forLoanHistoryStorage();

  protected final ServicePointsFixture servicePointsFixture
    = new ServicePointsFixture(servicePointsClient);

  protected final LocationsFixture locationsFixture = new LocationsFixture(
    locationsClient, institutionsClient, campusesClient, librariesClient,
    servicePointsFixture);

  protected final LoanTypesFixture loanTypesFixture = new LoanTypesFixture(
    ResourceClient.forLoanTypes());

  protected final MaterialTypesFixture materialTypesFixture
    = new MaterialTypesFixture(ResourceClient.forMaterialTypes());

  protected final UserManualBlocksFixture userManualBlocksFixture
    = new UserManualBlocksFixture(ResourceClient.forUserManualBlocks());

  protected final LoanPoliciesFixture loanPoliciesFixture
    = new LoanPoliciesFixture(loanPolicyClient, fixedDueDateScheduleClient);

  protected final RequestPoliciesFixture requestPoliciesFixture
    = new RequestPoliciesFixture(requestPolicyClient);

  protected final NoticePoliciesFixture noticePoliciesFixture
    = new NoticePoliciesFixture(noticePolicyClient);

  protected final OverdueFinePoliciesFixture overdueFinePoliciesFixture = new OverdueFinePoliciesFixture();

  protected final CirculationRulesFixture circulationRulesFixture
    = new CirculationRulesFixture(
      new RestAssuredClient(getOkapiHeadersFromContext()));

  protected final ItemsFixture itemsFixture = new ItemsFixture(
    materialTypesFixture, loanTypesFixture, locationsFixture,
    instanceTypesClient, contributorNameTypesClient);

  protected final AddressTypesFixture addressTypesFixture
    = new AddressTypesFixture(ResourceClient.forAddressTypes());

  protected final PatronGroupsFixture patronGroupsFixture
    = new PatronGroupsFixture(patronGroupsClient);

  protected final ProxyRelationshipsFixture proxyRelationshipsFixture
    = new ProxyRelationshipsFixture(proxyRelationshipsClient);

  protected final UsersFixture usersFixture = new UsersFixture(usersClient,
    patronGroupsFixture);

  protected final LoansFixture loansFixture = new LoansFixture();

  protected final CheckOutFixture checkOutFixture = new CheckOutFixture(
    usersFixture, servicePointsFixture);

  protected final CheckInFixture checkInFixture = new CheckInFixture(
    servicePointsFixture);

  protected final ChangeDueDateFixture changeDueDateFixture = new ChangeDueDateFixture();

  protected final CancellationReasonsFixture cancellationReasonsFixture
    = new CancellationReasonsFixture(ResourceClient.forCancellationReasons());

  protected final RequestsFixture requestsFixture = new RequestsFixture(
    requestsClient, cancellationReasonsFixture, servicePointsFixture);

  protected final InstancesFixture instancesFixture
    = new InstancesFixture(instanceTypesClient, contributorNameTypesClient);

  protected final HoldingsFixture holdingsFixture
    = new HoldingsFixture();

  protected final ScheduledNoticeProcessingClient scheduledNoticeProcessingClient =
    new ScheduledNoticeProcessingClient();

  protected final RequestQueueFixture requestQueueFixture =
    new RequestQueueFixture(restAssuredClient);

  protected final TemplateFixture templateFixture = new TemplateFixture(templateClient);
  protected final IdentifierTypesFixture identifierTypesFixture = new IdentifierTypesFixture();

  protected final FeeFineOwnerFixture feeFineOwnerFixture = new FeeFineOwnerFixture();
  protected final FeeFineTypeFixture feeFineTypeFixture = new FeeFineTypeFixture();
  protected final LostItemFeePoliciesFixture lostItemFeePoliciesFixture = new LostItemFeePoliciesFixture();
  protected final NoteTypeFixture noteTypeFixture = new NoteTypeFixture();
  protected final DeclareLostFixtures declareLostFixtures = new DeclareLostFixtures();
  protected final ClaimItemReturnedFixture claimItemReturnedFixture = new ClaimItemReturnedFixture(restAssuredClient);
  protected final FeeFineAccountFixture feeFineAccountFixture = new FeeFineAccountFixture();
  protected final EventSubscribersFixture eventSubscribersFixture = new EventSubscribersFixture();
  protected final AutomatedPatronBlocksFixture automatedPatronBlocksFixture =
    new AutomatedPatronBlocksFixture();

  protected final TenantActivationFixture tenantActivationFixture = new TenantActivationFixture(restAssuredClient);
  @Delegate
  // The @Delegate annotation will instruct lombok to auto generate delegating methods
  // in this class for all public methods of the PoliciesActivationFixture class
  protected final PoliciesActivationFixture policiesActivation = new PoliciesActivationFixture();
  protected final AgeToLostFixture ageToLostFixture =
    new AgeToLostFixture(itemsFixture, usersFixture, checkOutFixture);

  protected APITests() {
    this(true, false);
  }

  protected APITests(boolean initialiseCirculationRules, boolean enableLoanHistory) {
    this.initialiseCirculationRules = initialiseCirculationRules;
    setLoanHistoryEnabled(enableLoanHistory);
  }

  @BeforeClass
  public static void beforeAll() throws InterruptedException, ExecutionException,
    TimeoutException {

    if (okapiAlreadyDeployed) {
      return;
    }

    deployVerticles();
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      try {
        undeployVerticles();
      } catch (Exception ex) {
        Assert.fail("Failed to undeploy verticle: " + ex);
      }
    }));
    okapiAlreadyDeployed = true;
  }

  @Before
  public void beforeEach() throws InterruptedException {
    if (initialiseCirculationRules) {
      useDefaultRollingPolicyCirculationRules();
    }

    usersFixture.defaultAdmin();
    noteTypeFixture.generalNoteType();

    FakePubSub.clearPublishedEvents();
    FakePubSub.setFailPublishingWithBadRequestError(false);
  }

  @After
  public void afterEach() {
    forTenantStorage().deleteAll();

    mockClockManagerToReturnDefaultDateTime();
  }

  protected void assertLoanHasFeeFinesProperties(JsonObject loan,
                                                 double remainingAmount) {

    hasProperty("amountRemainingToPay", loan.getJsonObject("feesAndFines"),
      "loan", remainingAmount);
  }

  protected void loanHasLoanPolicyProperties(JsonObject loan,
    IndividualResource loanPolicy) {

    hasProperty("loanPolicyId", loan, "loan", loanPolicy.getId().toString());
    hasProperty("loanPolicy", loan, "loan");

    JsonObject loanPolicyObject = loan.getJsonObject("loanPolicy");

    hasProperty("name", loanPolicyObject, "loan policy",
      loanPolicy.getJson().getString("name"));
  }

  protected void loanHasOverdueFinePolicyProperties(JsonObject loan, IndividualResource loanPolicy) {
    hasProperty("overdueFinePolicyId", loan, "loan", loanPolicy.getId().toString());
    hasProperty("overdueFinePolicy", loan, "loan");
    JsonObject loanPolicyObject = loan.getJsonObject("overdueFinePolicy");
    hasProperty("name", loanPolicyObject, "overdue fine policy", loanPolicy.getJson().getString("name"));
  }

  protected void loanHasLostItemPolicyProperties(JsonObject loan, IndividualResource loanPolicy) {
    hasProperty("lostItemPolicyId", loan, "loan", loanPolicy.getId().toString());
    hasProperty("lostItemPolicy", loan, "loan");
    JsonObject loanPolicyObject = loan.getJsonObject("lostItemPolicy");
    hasProperty("name", loanPolicyObject, "lost item policy", loanPolicy.getJson().getString("name"));
  }

  protected void loanHasPatronGroupProperties(JsonObject loan,
    String patronGroupValue) {

    JsonObject patronGroupObject = loan.getJsonObject(PATRON_GROUP_AT_CHECKOUT);

    hasProperty("id", patronGroupObject, "patron group at checkout");
    hasProperty("name", patronGroupObject, "patron group at checkout");
    hasProperty("name", patronGroupObject, "patron group at checkout", patronGroupValue);
  }

  protected void hasProperty(String property, JsonObject resource, String type) {
    assertThat(String.format("%s should have an %s: %s", type, property, resource),
      resource.containsKey(property), is(true));
  }

  protected void hasProperty(String property, JsonObject resource, String type,
    Object value) {

    assertThat(String.format("%s should have an %s: %s", type, property, resource),
      resource.getMap().get(property), equalTo(value));
  }

  protected void doesNotHaveProperty(String property, JsonObject resource,
    String type) {

    assertThat(String.format("%s should NOT have an %s: %s", type, property,
      resource), resource.getValue(property), is(nullValue()));
  }

  protected void mockClockManagerToReturnFixedDateTime(DateTime dateTime) {
    ClockManager.getClockManager().setClock(
      Clock.fixed(
        Instant.ofEpochMilli(dateTime.getMillis()),
        ZoneOffset.UTC));
  }

  protected void mockClockManagerToReturnDefaultDateTime() {
    ClockManager.getClockManager().setDefaultClock();
  }
}
