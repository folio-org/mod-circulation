package api.support;

import api.APITestSuite;
import api.support.fixtures.ItemsFixture;
import api.support.fixtures.LoansFixture;
import api.support.fixtures.RequestsFixture;
import api.support.fixtures.UsersFixture;
import api.support.http.InterfaceUrls;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.OkapiHttpClient;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.net.MalformedURLException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public abstract class APITests {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static boolean runningOnOwn;

  protected final OkapiHttpClient client = APITestSuite.createClient(exception ->
    log.error("Request to circulation module failed:", exception));

  private final boolean initialiseLoanRules;
  private final ResourceClient userProxiesClient = ResourceClient.forUsersProxy(client);
  protected final ResourceClient usersClient = ResourceClient.forUsers(client);
  protected final ResourceClient itemsClient = ResourceClient.forItems(client);
  protected final ResourceClient requestsClient = ResourceClient.forRequests(client);
  protected final ResourceClient loansClient = ResourceClient.forLoans(client);
  protected final ResourceClient holdingsClient = ResourceClient.forHoldings(client);
  protected final ResourceClient instancesClient = ResourceClient.forInstances(client);
  protected final ResourceClient loansStorageClient = ResourceClient.forLoansStorage(client);
  protected final ResourceClient loanPolicyClient = ResourceClient.forLoanPolicies(client);
  protected final ResourceClient fixedDueDateScheduleClient = ResourceClient.forFixedDueDateSchedules(client);

  protected final ItemsFixture itemsFixture = new ItemsFixture(client);
  protected final LoansFixture loansFixture = new LoansFixture(loansClient, client);
  protected final RequestsFixture requestsFixture = new RequestsFixture(requestsClient);
  protected final UsersFixture usersFixture = new UsersFixture(usersClient, userProxiesClient);

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

    if(APITestSuite.isNotInitialised()) {
      System.out.println("Running test on own, initialising suite manually");
      runningOnOwn = true;
      APITestSuite.before();
    }
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

    usersClient.deleteAllIndividually();

    APITestSuite.createUsers();

    if(initialiseLoanRules) {
      useDefaultRollingPolicyLoanRules();
    }
  }

  @AfterClass
  public static void afterAll()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    if(runningOnOwn) {
      System.out.println("Running test on own, un-initialising suite manually");
      APITestSuite.after();
    }
  }

  @Before
  public void afterEach()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    for (UUID policyId : policiesToDelete) {
      loanPolicyClient.delete(policyId);
    }

    policiesToDelete.clear();

    for (UUID scheduleId : schedulesToDelete) {
      fixedDueDateScheduleClient.delete(scheduleId);
    }

    schedulesToDelete.clear();
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
}
