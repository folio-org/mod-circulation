package api.support;


import static api.APITestSuite.createClient;
import static api.APITestSuite.createCommonRecords;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.lang.invoke.MethodHandles;
import java.net.MalformedURLException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.http.client.OkapiHttpClient;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import api.APITestSuite;
import api.support.fixtures.ItemsFixture;
import api.support.fixtures.LoanTypesFixture;
import api.support.fixtures.LoansFixture;
import api.support.fixtures.LocationsFixture;
import api.support.fixtures.MaterialTypesFixture;
import api.support.fixtures.PatronGroupsFixture;
import api.support.fixtures.ProxyRelationshipsFixture;
import api.support.fixtures.RequestsFixture;
import api.support.fixtures.ServicePointsFixture;
import api.support.fixtures.UsersFixture;
import api.support.http.InterfaceUrls;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;

public abstract class APITests {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  //Temporarily static to ease moving code from test suite
  protected final OkapiHttpClient client = createClient(exception ->
    log.error("Request to circulation module failed:", exception));

  private final boolean initialiseLoanRules;

  private final ResourceClient servicePointsClient = ResourceClient.forServicePoints(client);

  private ResourceClient institutionsClient = ResourceClient.forInstitutions(client);
  private ResourceClient campusesClient = ResourceClient.forCampuses(client);
  private ResourceClient librariesClient = ResourceClient.forLibraries(client);
  private final ResourceClient locationsClient = ResourceClient.forLocations(client);

  private final ResourceClient patronGroupsClient = ResourceClient.forPatronGroups(client);
  protected final ResourceClient usersClient = ResourceClient.forUsers(client);
  private final ResourceClient proxyRelationshipsClient
    = ResourceClient.forProxyRelationships(client);

  protected final ResourceClient instancesClient = ResourceClient.forInstances(client);
  private final ResourceClient holdingsClient = ResourceClient.forHoldings(client);
  protected final ResourceClient itemsClient = ResourceClient.forItems(client);

  protected final ResourceClient loansClient = ResourceClient.forLoans(client);
  protected final ResourceClient loansStorageClient = ResourceClient.forLoansStorage(client);

  protected final ResourceClient requestsClient = ResourceClient.forRequests(client);

  protected final ResourceClient fixedDueDateScheduleClient
    = ResourceClient.forFixedDueDateSchedules(client);

  protected final ResourceClient loanPolicyClient = ResourceClient.forLoanPolicies(client);

  private final ResourceClient instanceTypesClient = ResourceClient.forInstanceTypes(client);
  private final ResourceClient contributorNameTypesClient
    = ResourceClient.forContributorNameTypes(client);

  protected final ServicePointsFixture servicePointsFixture
    = new ServicePointsFixture(servicePointsClient);

  protected final LocationsFixture locationsFixture = new LocationsFixture(
    locationsClient, institutionsClient, campusesClient, librariesClient,
    servicePointsFixture);

  protected final LoanTypesFixture loanTypesFixture = new LoanTypesFixture(
    ResourceClient.forLoanTypes(client));

  protected final MaterialTypesFixture materialTypesFixture = new MaterialTypesFixture(
    ResourceClient.forMaterialTypes(client));

  protected final ItemsFixture itemsFixture = new ItemsFixture(client,
    materialTypesFixture, loanTypesFixture, locationsFixture,
    instanceTypesClient, contributorNameTypesClient);

  protected final PatronGroupsFixture patronGroupsFixture
    = new PatronGroupsFixture(patronGroupsClient);

  protected final ProxyRelationshipsFixture proxyRelationshipsFixture
    = new ProxyRelationshipsFixture(proxyRelationshipsClient);

  protected final UsersFixture usersFixture = new UsersFixture(usersClient,
    patronGroupsFixture);

  protected final LoansFixture loansFixture = new LoansFixture(loansClient,
    client, usersFixture, servicePointsFixture);

  protected final RequestsFixture requestsFixture = new RequestsFixture(requestsClient);

  protected final Set<UUID> schedulesToDelete = new HashSet<>();
  protected final Set<UUID> policiesToDelete = new HashSet<>();

  protected APITests() {
    this(true);
  }

  protected APITests(boolean initialiseLoanRules) {
    this.initialiseLoanRules = initialiseLoanRules;
  }

  @BeforeClass
  public static void beforeAll()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    APITestSuite.deployVerticles();

    //Delete everything first just in case
    APITestSuite.deleteAllRecords();

    createCommonRecords();
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

    if (initialiseLoanRules) {
      useDefaultRollingPolicyLoanRules();
    }
  }

  @AfterClass
  public static void afterAll()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    deleteCommonRecords();

    APITestSuite.undeployVerticles();
  }

  @After
  public void afterEach()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

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

    for (UUID policyId : policiesToDelete) {
      loanPolicyClient.delete(policyId);
    }

    policiesToDelete.clear();

    for (UUID scheduleId : schedulesToDelete) {
      fixedDueDateScheduleClient.delete(scheduleId);
    }

    schedulesToDelete.clear();

    usersFixture.cleanUp();

    patronGroupsFixture.cleanUp();
  }

  //Needs to be done each time as some tests manipulate the rules
  private void useDefaultRollingPolicyLoanRules()
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    log.info("Using rolling loan policy as fallback policy");
    useLoanPolicyAsFallback(APITestSuite.canCirculateRollingLoanPolicyId());
  }

  protected void useExampleFixedPolicyLoanRules()
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    log.info("Using fixed loan policy as fallback policy");
    useLoanPolicyAsFallback(APITestSuite.canCirculateFixedLoanPolicyId());
  }

  protected void useLoanPolicyAsFallback(UUID loanPolicyId)
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    updateLoanRules(loanPolicyId);
    warmUpApplyEndpoint();
  }

  private void updateLoanRules(UUID loanPolicyId)
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    String rule = String.format("priority: t, s, c, b, a, m, g%nfallback-policy: %s%n",
      loanPolicyId);

    JsonObject loanRulesRequest = new JsonObject()
      .put("loanRulesAsTextFile", rule);

    CompletableFuture<Response> completed = new CompletableFuture<>();

    client.put(InterfaceUrls.loanRulesUrl(), loanRulesRequest,
      ResponseHandler.any(completed));

    Response response = completed.get(5, TimeUnit.SECONDS);

    assertThat(String.format(
      "Failed to set loan rules: %s", response.getBody()),
      response.getStatusCode(), is(204));
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
      "Failed to apply loan rules: %s", response.getBody()),
      response.getStatusCode(), is(200));
  }

  private static void deleteCommonRecords()
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

    APITestSuite.deleteAddressTypes();

    APITestSuite.deleteLoanPolicies();
    APITestSuite.deleteCancellationReasons();
  }
}
