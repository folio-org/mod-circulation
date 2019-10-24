package api.support;

import static api.support.APITestContext.createClient;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.lang.invoke.MethodHandles;
import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import api.support.builders.LoanPolicyBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.fixtures.OverdueFinePoliciesFixture;
import org.folio.circulation.domain.representations.LoanProperties;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.OkapiHttpClient;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import api.support.fixtures.AddressTypesFixture;
import api.support.fixtures.CancellationReasonsFixture;
import api.support.fixtures.CirculationRulesFixture;
import api.support.fixtures.EndPatronSessionClient;
import api.support.fixtures.HoldingsFixture;
import api.support.fixtures.InstancesFixture;
import api.support.fixtures.ItemsFixture;
import api.support.fixtures.LoanPoliciesFixture;
import api.support.fixtures.LoanTypesFixture;
import api.support.fixtures.LoansFixture;
import api.support.fixtures.LocationsFixture;
import api.support.fixtures.MaterialTypesFixture;
import api.support.fixtures.NoticePoliciesFixture;
import api.support.fixtures.PatronGroupsFixture;
import api.support.fixtures.ProxyRelationshipsFixture;
import api.support.fixtures.RequestPoliciesFixture;
import api.support.fixtures.RequestsFixture;
import api.support.fixtures.ScheduledNoticeProcessingClient;
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

  private final boolean initialiseCirculationRules;

  private final ResourceClient servicePointsClient = ResourceClient.forServicePoints(client);

  private final ResourceClient institutionsClient = ResourceClient.forInstitutions(client);
  private final ResourceClient campusesClient = ResourceClient.forCampuses(client);
  private final ResourceClient librariesClient = ResourceClient.forLibraries(client);
  private final ResourceClient locationsClient = ResourceClient.forLocations(client);

  protected final ResourceClient configClient = ResourceClient.forConfiguration(client);

  private final ResourceClient patronGroupsClient
    = ResourceClient.forPatronGroups(client);

  protected final ResourceClient usersClient = ResourceClient.forUsers(client);
  private final ResourceClient proxyRelationshipsClient
    = ResourceClient.forProxyRelationships(client);

  protected final ResourceClient instancesClient = ResourceClient.forInstances(client);
  protected final ResourceClient holdingsClient = ResourceClient.forHoldings(client);
  protected final ResourceClient itemsClient = ResourceClient.forItems(client);

  protected final ResourceClient loansClient = ResourceClient.forLoans(client);
  protected final ResourceClient accountsClient = ResourceClient.forAccounts(client);
  protected final ResourceClient feeFineActionsClient = ResourceClient.forFeeFineActions(client);

  protected final ResourceClient loansStorageClient
    = ResourceClient.forLoansStorage(client);

  protected final ResourceClient requestsStorageClient
    = ResourceClient.forRequestsStorage(client);

  protected final ResourceClient requestsClient = ResourceClient.forRequests(client);

  protected final ResourceClient fixedDueDateScheduleClient
    = ResourceClient.forFixedDueDateSchedules(client);

  protected final ResourceClient loanPolicyClient
    = ResourceClient.forLoanPolicies(client);

  protected final ResourceClient requestPolicyClient
    = ResourceClient.forRequestPolicies(client);

  protected final ResourceClient noticePolicyClient
    = ResourceClient.forNoticePolicies(client);

  protected final ResourceClient overdueFinePolicyClient
    = ResourceClient.forOverdueFinePolicies(client);

  private final ResourceClient instanceTypesClient
    = ResourceClient.forInstanceTypes(client);

  private final ResourceClient contributorNameTypesClient
    = ResourceClient.forContributorNameTypes(client);

  protected final ResourceClient patronNoticesClient =
    ResourceClient.forPatronNotices(client);

  protected final ResourceClient scheduledNoticesClient =
    ResourceClient.forScheduledNotices(client);

  protected final ResourceClient patronSessionRecordsClient =
    ResourceClient.forPatronSessionRecords(client);

  protected final EndPatronSessionClient endPatronSessionClient =
    new EndPatronSessionClient();

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

  protected final OverdueFinePoliciesFixture overdueFinePoliciesFixture
    = new OverdueFinePoliciesFixture(overdueFinePolicyClient);

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
    usersFixture, servicePointsFixture);

  protected final CancellationReasonsFixture cancellationReasonsFixture
    = new CancellationReasonsFixture(ResourceClient.forCancellationReasons(client));

  protected final RequestsFixture requestsFixture = new RequestsFixture(
    requestsClient, cancellationReasonsFixture, servicePointsFixture);

  protected final InstancesFixture instancesFixture
    = new InstancesFixture(instanceTypesClient, contributorNameTypesClient, client);

  protected final HoldingsFixture holdingsFixture
    = new HoldingsFixture(client);

  protected final ScheduledNoticeProcessingClient scheduledNoticeProcessingClient =
    new ScheduledNoticeProcessingClient();

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
    configClient.deleteAll();

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
    configClient.deleteAll();
    patronNoticesClient.deleteAll();
    scheduledNoticesClient.deleteAll();
    patronSessionRecordsClient.deleteAllIndividually();

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
    instancesFixture.cleanUp();
  }

  //Needs to be done each time as some tests manipulate the rules
  private void useDefaultRollingPolicyCirculationRules()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    log.info("Using rolling loan policy as fallback policy");
    useFallbackPolicies(
      loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.activeNotice().getId(),
      overdueFinePoliciesFixture.facultyStandard().getId()
    );
  }

  protected void useExampleFixedPolicyCirculationRules()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    log.info("Using fixed loan policy as fallback policy");
    useFallbackPolicies(
      loanPoliciesFixture.canCirculateFixed().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.activeNotice().getId(),
      overdueFinePoliciesFixture.facultyStandard().getId()
    );
  }

  protected void useFallbackPolicies(UUID loanPolicyId, UUID requestPolicyId,
                                     UUID noticePolicyId, UUID overdueFinePolicyId)
    throws InterruptedException,
    ExecutionException,
    TimeoutException, MalformedURLException {

    circulationRulesFixture.updateCirculationRules(loanPolicyId, requestPolicyId,
      noticePolicyId, overdueFinePolicyId);

    warmUpApplyEndpoint();
  }


  /**
   * This method uses the loan policy, allowAllRequestPolicy request policy,
   * inactiveNotice notice policy, facultyStandard overdue fine policy from
   * the loanPolicyBuilder.
   * @param loanPolicyBuilder - loan policy builder.
   */
  protected void setFallbackPolicies(LoanPolicyBuilder loanPolicyBuilder)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {
    final IndividualResource loanPolicy = loanPoliciesFixture.create(loanPolicyBuilder);
    useFallbackPolicies(loanPolicy.getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.inactiveNotice().getId(),
      overdueFinePoliciesFixture.facultyStandard().getId());
  }

  /**
   * This method uses the loan policy, allowAllRequestPolicy request policy,
   * activeNotice notice policy, facultyStandard overdue fine policy from
   * the loanPolicyBuilder.
   * @param loanPolicyBuilder - loan policy builder.
   */
  protected void useWithActiveNotice(LoanPolicyBuilder loanPolicyBuilder)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {
    useFallbackPolicies(
      loanPoliciesFixture.create(loanPolicyBuilder).getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.activeNotice().getId(),
      overdueFinePoliciesFixture.facultyStandard().getId()
    );
  }

  /**
   * This method uses the loan policy, allowAllRequestPolicy request policy,
   * activeNotice notice policy, facultyStandard overdue fine policy from
   * the loanPolicyBuilder.
   * @param loanPolicyBuilder - loan policy builder.
   */
  protected void use(LoanPolicyBuilder loanPolicyBuilder)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {
    useFallbackPolicies(
      loanPolicyClient.create(loanPolicyBuilder).getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.activeNotice().getId(),
      overdueFinePoliciesFixture.facultyStandard().getId());
  }

  /**
   * This method uses the loan policy, allowAllRequestPolicy request policy,
   * activeNotice notice policy, facultyStandard overdue fine policy from
   * the loanPolicyBuilder and noticePolicyBuilder.
   * @param loanPolicyBuilder - loan policy builder.
   */
  protected void use(LoanPolicyBuilder loanPolicyBuilder,
                     NoticePolicyBuilder noticePolicyBuilder)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {
    useFallbackPolicies(
      loanPoliciesFixture.create(loanPolicyBuilder).getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicyBuilder).getId(),
      overdueFinePoliciesFixture.facultyStandard().getId());

  }

  /**
   * This method uses notice policy, canCirculateRolling loan policy,
   * allowAllRequestPolicy request policy,
   * facultyStandard overdue fine policy from
   * the loanPolicyBuilder.
   * @param noticePolicy - notice policy.
   */
  protected void use(NoticePolicyBuilder noticePolicy)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {
    useFallbackPolicies(
      loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicy).getId(),
      overdueFinePoliciesFixture.facultyStandard().getId());
  }

  /**
   * This method uses notice policy, canCirculateRolling loan policy,
   * allowAllRequestPolicy request policy, facultyStandard overdue fine policy from
   * the loanPolicyBuilder.
   * @param noticePolicy - notice policy.
   */
  protected void useWithPaging(NoticePolicyBuilder noticePolicy)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {
    useFallbackPolicies(
      loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.pageRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicy).getId(),
      overdueFinePoliciesFixture.facultyStandard().getId());
  }

  protected void warmUpApplyEndpoint()
    throws InterruptedException,
    ExecutionException,
    TimeoutException, MalformedURLException {

    CompletableFuture<Response> completed = new CompletableFuture<>();

    client.get(InterfaceUrls.circulationRulesUrl("/loan-policy"
        + String.format("?item_type_id=%s&loan_type_id=%s&patron_type_id=%s&location_id=%s",
      UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), locationsFixture.mezzanineDisplayCase().getId())),
      ResponseHandler.any(completed));

    Response response = completed.get(5, TimeUnit.SECONDS);

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

    ResourceClient.forAccounts(cleanupClient).deleteAll();
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

  protected void loanHasFeeFinesProperties(JsonObject loan, double remainingAmount) {
    hasProperty("amountRemainingToPay", loan.getJsonObject("feesAndFines"),
      "loan", remainingAmount);
  }

  protected void loanHasLoanPolicyProperties(JsonObject loan, IndividualResource loanPolicy) {
    hasProperty("loanPolicyId", loan, "loan", loanPolicy.getId().toString());
    hasProperty("loanPolicy", loan, "loan");
    JsonObject loanPolicyObject = loan.getJsonObject("loanPolicy");
    hasProperty("name", loanPolicyObject, "loan policy", loanPolicy.getJson().getString("name"));
  }

  protected void loanHasPatronGroupProperties(JsonObject loan, String patronGroupValue) {
    JsonObject patronGroupObject = loan.getJsonObject(LoanProperties.PATRON_GROUP_AT_CHECKOUT);
    hasProperty("id", patronGroupObject, "patron group at checkout");
    hasProperty("name", patronGroupObject, "patron group at checkout");
    hasProperty("name", patronGroupObject, "patron group at checkout", patronGroupValue);
  }

  protected void hasProperty(String property, JsonObject resource, String type) {
    assertThat(String.format("%s should have an %s: %s",
      type, property, resource),
      resource.containsKey(property), is(true));
  }


  protected void hasProperty(String property, JsonObject resource, String type, Object value) {
    assertThat(String.format("%s should have an %s: %s",
      type, property, resource),
      resource.getMap().get(property), equalTo(value));
  }


  protected void doesNotHaveProperty(String property, JsonObject resource, String type) {
    assertThat(String.format("%s should NOT have an %s: %s",
            type, property, resource),
            resource.getValue(property), is(nullValue()));
  }

  protected void setInvalidLoanPolicyReferenceInRules(String invalidLoanPolicyReference)
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    circulationRulesFixture.updateCirculationRules(
      circulationRulesFixture.soleFallbackPolicyRule(invalidLoanPolicyReference,
        requestPoliciesFixture.allowAllRequestPolicy().getId().toString(),
        noticePoliciesFixture.inactiveNotice().getId().toString(),
        overdueFinePoliciesFixture.facultyStandard().getId().toString()));
  }

  protected void setInvalidNoticePolicyReferenceInRules(String invalidNoticePolicyReference)
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    circulationRulesFixture.updateCirculationRules(
      circulationRulesFixture.soleFallbackPolicyRule(
        loanPoliciesFixture.canCirculateRolling().getId().toString(),
        requestPoliciesFixture.allowAllRequestPolicy().getId().toString(),
        invalidNoticePolicyReference,
        overdueFinePoliciesFixture.facultyStandard().getId().toString()));
  }
}
