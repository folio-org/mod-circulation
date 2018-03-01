package org.folio.circulation.api.support;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.api.APITestSuite;
import org.folio.circulation.api.support.fixtures.ItemsFixture;
import org.folio.circulation.api.support.fixtures.LoansFixture;
import org.folio.circulation.api.support.fixtures.RequestsFixture;
import org.folio.circulation.api.support.fixtures.UsersFixture;
import org.folio.circulation.api.support.http.InterfaceUrls;
import org.folio.circulation.api.support.http.ResourceClient;
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
  protected final ResourceClient usersClient = ResourceClient.forUsers(client);
  protected final ResourceClient itemsClient = ResourceClient.forItems(client);
  protected final ResourceClient requestsClient = ResourceClient.forRequests(client);
  protected final ResourceClient loansClient = ResourceClient.forLoans(client);
  protected final ResourceClient holdingsClient = ResourceClient.forHoldings(client);
  protected final ResourceClient instancesClient = ResourceClient.forInstances(client);

  protected final ItemsFixture itemsFixture = new ItemsFixture(client);
  protected final LoansFixture loansFixture = new LoansFixture(loansClient, client);
  protected final RequestsFixture requestsFixture = new RequestsFixture(requestsClient);
  protected final UsersFixture usersFixture = new UsersFixture(usersClient);

  protected APITests() {
    this(true);
  }

  protected APITests(boolean initialiseLoanRules) {
    this.initialiseLoanRules = initialiseLoanRules;
  }

  @BeforeClass
  public static void before()
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

  @AfterClass
  public static void after()
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
      setDefaultLoanRules();
      warmUpApplyEndpoint();
    }
  }

  //Needs to be done each time as some tests manipulate the rules
  private void setDefaultLoanRules()
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    String rule = String.format("fallback-policy: %s\n", APITestSuite.canCirculateLoanPolicyId());

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
