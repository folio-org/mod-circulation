package api.support;

import api.support.fixtures.AddressTypesFixture;
import api.support.fixtures.CancellationReasonsFixture;
import api.support.fixtures.ItemsFixture;
import api.support.fixtures.LoanPoliciesFixture;
import api.support.fixtures.CirculationRulesFixture;
import api.support.fixtures.LoanTypesFixture;
import api.support.fixtures.LoansFixture;
import api.support.fixtures.LocationsFixture;
import api.support.fixtures.MaterialTypesFixture;
import api.support.fixtures.NoticePoliciesFixture;
import api.support.fixtures.PatronGroupsFixture;
import api.support.fixtures.ProxyRelationshipsFixture;
import api.support.fixtures.RequestPoliciesFixture;
import api.support.fixtures.RequestsFixture;
import api.support.fixtures.ServicePointsFixture;
import api.support.fixtures.UsersFixture;
import api.support.http.InterfaceUrls;
import api.support.http.ResourceClient;
import org.folio.circulation.support.http.client.OkapiHttpClient;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static api.support.APITestContext.createClient;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public abstract class APITests {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  //Temporarily static to ease moving code from test suite
  protected final OkapiHttpClient client = createClient(exception ->
    log.error("Request to circulation module failed:", exception));

  private final boolean initialiseCirculationRules;

  private final ResourceClient servicePointsClient = ResourceClient.forServicePoints(client);

  private final ResourceClient institutionsClient = ResourceClient.forInstitutions(client);
  private final ResourceClient campusesClient = ResourceClient.forCampuses(client);
  private final ResourceClient librariesClient = ResourceClient.forLibraries(client);
  private final ResourceClient locationsClient = ResourceClient.forLocations(client);

  private final ResourceClient patronGroupsClient
    = ResourceClient.forPatronGroups(client);

  protected final ResourceClient usersClient = ResourceClient.forUsers(client);
  private final ResourceClient proxyRelationshipsClient
    = ResourceClient.forProxyRelationships(client);

  protected final ResourceClient instancesClient = ResourceClient.forInstances(client);
  private final ResourceClient holdingsClient = ResourceClient.forHoldings(client);
  protected final ResourceClient itemsClient = ResourceClient.forItems(client);

  protected final ResourceClient loansClient = ResourceClient.forLoans(client);
  protected final ResourceClient loansStorageClient
    = ResourceClient.forLoansStorage(client);

  protected final ResourceClient requestsClient = ResourceClient.forRequests(client);

  protected final ResourceClient fixedDueDateScheduleClient
    = ResourceClient.forFixedDueDateSchedules(client);

  protected final ResourceClient loanPolicyClient
    = ResourceClient.forLoanPolicies(client);

  protected final ResourceClient requestPolicyClient
    = ResourceClient.forRequestPolicies(client);

  protected final ResourceClient noticePolicyClient
    = ResourceClient.forNoticePolicies(client);

  private final ResourceClient instanceTypesClient
    = ResourceClient.forInstanceTypes(client);

  private final ResourceClient contributorNameTypesClient
    = ResourceClient.forContributorNameTypes(client);

  protected final ServicePointsFixture servicePointsFixture
    = new ServicePointsFixture(servicePointsClient);

  protected final LocationsFixture locationsFixture = new LocationsFixture(
    locationsClient, institutionsClient, campusesClient, librariesClient,
    servicePointsFixture);

  protected final LoanTypesFixture loanTypesFixture = new LoanTypesFixture(
    ResourceClient.forLoanTypes(client));

  protected final MaterialTypesFixture materialTypesFixture
    = new MaterialTypesFixture(ResourceClient.forMaterialTypes(client));

  protected final LoanPoliciesFixture loanPoliciesFixture
    = new LoanPoliciesFixture(loanPolicyClient, fixedDueDateScheduleClient);

  protected final RequestPoliciesFixture requestPoliciesFixture
    = new RequestPoliciesFixture(requestPolicyClient);

  protected final NoticePoliciesFixture noticePoliciesFixture
    = new NoticePoliciesFixture(noticePolicyClient);

  protected final CirculationRulesFixture circulationRulesFixture
    = new CirculationRulesFixture(client);

  protected final ItemsFixture itemsFixture = new ItemsFixture(client,
    materialTypesFixture, loanTypesFixture, locationsFixture,
    instanceTypesClient, contributorNameTypesClient);

  protected final AddressTypesFixture addressTypesFixture
    = new AddressTypesFixture(ResourceClient.forAddressTypes(client));

  protected final PatronGroupsFixture patronGroupsFixture
    = new PatronGroupsFixture(patronGroupsClient);

  protected final ProxyRelationshipsFixture proxyRelationshipsFixture
    = new ProxyRelationshipsFixture(proxyRelationshipsClient);

  protected final UsersFixture usersFixture = new UsersFixture(usersClient,
    patronGroupsFixture);

  protected final LoansFixture loansFixture = new LoansFixture(loansClient,
    client, usersFixture, servicePointsFixture);

  protected final CancellationReasonsFixture cancellationReasonsFixture
    = new CancellationReasonsFixture(ResourceClient.forCancellationReasons(client));

  protected final RequestsFixture requestsFixture = new RequestsFixture(
    requestsClient, cancellationReasonsFixture);

  protected APITests() {
    this(true);
  }

  protected APITests(boolean initialiseCirculationRules) {
    this.initialiseCirculationRules = initialiseCirculationRules;
  }

  @BeforeClass
  public static void beforeAll()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    APITestContext.deployVerticles();

    //Delete everything first just in case
    deleteAllRecords();
  }

  @Before
  public void beforeEach()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    requestsClient.deleteAll();
    loansClient.deleteAll();

    itemsClient.deleteAll();
    holdingsClient.deleteAll();
    instancesClient.deleteAll();

    //TODO: Only cleans up reference records, move items, holdings records
    // and instances into here too
    itemsFixture.cleanUp();

    usersClient.deleteAllIndividually();

    if (initialiseCirculationRules) {
      useDefaultRollingPolicyCirculationRules();
    }
  }

  @AfterClass
  public static void afterAll()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    deleteOftenCreatedRecords();

    APITestContext.undeployVerticles();
  }

  @After
  public void afterEach()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    requestsClient.deleteAll();
    loansClient.deleteAll();

    itemsClient.deleteAll();
    holdingsClient.deleteAll();
    instancesClient.deleteAll();

    //TODO: Only cleans up reference records, move items, holdings records
    // and instances into here too
    itemsFixture.cleanUp();

    materialTypesFixture.cleanUp();
    loanTypesFixture.cleanUp();

    locationsFixture.cleanUp();
    servicePointsFixture.cleanUp();

    loanPoliciesFixture.cleanUp();

    usersFixture.cleanUp();

    addressTypesFixture.cleanUp();
    patronGroupsFixture.cleanUp();

    cancellationReasonsFixture.cleanUp();
  }

  //Needs to be done each time as some tests manipulate the rules
  private void useDefaultRollingPolicyCirculationRules()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    log.info("Using rolling loan policy as fallback policy");
    useLoanPolicyAsFallback(
      loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.noAllowedTypes().getId(),
      noticePoliciesFixture.activeNotice().getId()
    );
  }

  protected void useExampleFixedPolicyCirculationRules()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    log.info("Using fixed loan policy as fallback policy");
    useLoanPolicyAsFallback(
      loanPoliciesFixture.canCirculateFixed().getId(),
      requestPoliciesFixture.noAllowedTypes().getId(),
      noticePoliciesFixture.activeNotice().getId()
    );
  }

  protected void useLoanPolicyAsFallback(UUID loanPolicyId, UUID requestPolicyId, UUID noticePolicyId)
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    circulationRulesFixture.updateCirculationRules(loanPolicyId, requestPolicyId, noticePolicyId);
    warmUpApplyEndpoint();
  }

  protected void warmUpApplyEndpoint()
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<Response> completed = new CompletableFuture<>();

    client.get(InterfaceUrls.loanRulesUrl("/apply"
        + String.format("?item_type_id=%s&loan_type_id=%s&patron_type_id=%s&shelving_location_id=%s",
      UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())),
      ResponseHandler.any(completed));

    Response response = completed.get(10, TimeUnit.SECONDS);

    assertThat(String.format(
      "Failed to apply circulation rules: %s", response.getBody()),
      response.getStatusCode(), is(200));
  }

  private static void deleteOftenCreatedRecords()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    OkapiHttpClient cleanupClient = createClient(exception ->
      log.error("Requests to delete all for clean up failed:", exception));

    ResourceClient.forRequests(cleanupClient).deleteAll();
    ResourceClient.forLoans(cleanupClient).deleteAll();

    ResourceClient.forItems(cleanupClient).deleteAll();
    ResourceClient.forHoldings(cleanupClient).deleteAll();
    ResourceClient.forInstances(cleanupClient).deleteAll();

    ResourceClient.forUsers(cleanupClient).deleteAllIndividually();
  }

  private static void deleteAllRecords()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    OkapiHttpClient client = APITestContext.createClient(exception ->
      log.error("Requests to delete all for clean up failed:", exception));

    ResourceClient.forRequests(client).deleteAll();
    ResourceClient.forLoans(client).deleteAll();

    ResourceClient.forItems(client).deleteAll();
    ResourceClient.forHoldings(client).deleteAll();
    ResourceClient.forInstances(client).deleteAll();

    ResourceClient.forLoanPolicies(client).deleteAllIndividually();
    ResourceClient.forFixedDueDateSchedules(client).deleteAllIndividually();

    ResourceClient.forMaterialTypes(client).deleteAllIndividually();
    ResourceClient.forLoanTypes(client).deleteAllIndividually();

    ResourceClient.forUsers(client).deleteAllIndividually();

    ResourceClient.forPatronGroups(client).deleteAllIndividually();
    ResourceClient.forAddressTypes(client).deleteAllIndividually();

    ResourceClient.forMaterialTypes(client).deleteAllIndividually();
    ResourceClient.forLoanTypes(client).deleteAllIndividually();
    ResourceClient.forLocations(client).deleteAllIndividually();
    ResourceClient.forServicePoints(client).deleteAllIndividually();
    ResourceClient.forContributorNameTypes(client).deleteAllIndividually();
    ResourceClient.forInstanceTypes(client).deleteAllIndividually();
    ResourceClient.forCancellationReasons(client).deleteAllIndividually();
  }
}
