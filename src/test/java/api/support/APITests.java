package api.support;

import static api.support.APITestContext.deployVerticles;
import static api.support.APITestContext.getOkapiHeadersFromContext;
import static api.support.APITestContext.undeployVerticles;
import static api.support.http.InterfaceUrls.circulationRulesUrl;
import static api.support.http.api.support.NamedQueryStringParameter.namedParameter;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.MatcherAssert.assertThat;

import static org.folio.circulation.domain.representations.LoanProperties.PATRON_GROUP_AT_CHECKOUT;

import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import api.support.builders.LoanPolicyBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.fixtures.AddressTypesFixture;
import api.support.fixtures.CancellationReasonsFixture;
import api.support.fixtures.CirculationRulesFixture;
import api.support.fixtures.EndPatronSessionClient;
import api.support.fixtures.ExpiredSessionProcessingClient;
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
import api.support.fixtures.UserManualBlocksFixture;
import api.support.fixtures.UsersFixture;
import api.support.http.QueryStringParameter;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.folio.circulation.support.ClockManager;
import org.folio.circulation.support.http.client.IndividualResource;

public abstract class APITests {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final RestAssuredClient restAssuredClient = new RestAssuredClient(
    getOkapiHeadersFromContext());

  private final boolean initialiseCirculationRules;

  private final ResourceClient servicePointsClient = ResourceClient.forServicePoints();

  private final ResourceClient institutionsClient = ResourceClient.forInstitutions();
  private final ResourceClient campusesClient = ResourceClient.forCampuses();
  private final ResourceClient librariesClient = ResourceClient.forLibraries();
  private final ResourceClient locationsClient = ResourceClient.forLocations();

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

  protected final OverdueFinePoliciesFixture overdueFinePoliciesFixture
    = new OverdueFinePoliciesFixture(overdueFinePolicyClient);

  protected final LostItemFeePoliciesFixture lostItemFeePoliciesFixture
    = new LostItemFeePoliciesFixture(lostItemFeePolicyClient);

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

  protected final LoansFixture loansFixture = new LoansFixture(
          usersFixture, servicePointsFixture);

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

  protected APITests() {
    this(true);
  }

  protected APITests(boolean initialiseCirculationRules) {
    this.initialiseCirculationRules = initialiseCirculationRules;
  }

  @BeforeClass
  public static void beforeAll() throws InterruptedException, ExecutionException,
    TimeoutException {

    deployVerticles();

    //Delete everything first just in case
    deleteAllRecords();
  }

  @Before
  public void beforeEach() throws InterruptedException {
    requestsClient.deleteAll();
    loansClient.deleteAll();

    itemsClient.deleteAll();
    holdingsClient.deleteAll();
    instancesClient.deleteAll();
    configClient.deleteAll();
    accountsClient.deleteAll();

    //TODO: Only cleans up reference records, move items, holdings records
    // and instances into here too
    itemsFixture.cleanUp();

    usersClient.deleteAllIndividually();

    checkInOperationClient.deleteAll();

    if (initialiseCirculationRules) {
      useDefaultRollingPolicyCirculationRules();
    }
  }

  @AfterClass
  public static void afterAll() throws InterruptedException, ExecutionException,
    TimeoutException {

    deleteOftenCreatedRecords();

    undeployVerticles();
  }

  @After
  public void afterEach() {
    requestsClient.deleteAll();
    loansClient.deleteAll();

    itemsClient.deleteAll();
    holdingsClient.deleteAll();
    instancesClient.deleteAll();
    configClient.deleteAll();
    patronNoticesClient.deleteAll();
    scheduledNoticesClient.deleteAll();
    patronSessionRecordsClient.deleteAllIndividually();
    templateFixture.deleteAll();

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
    userManualBlocksFixture.cleanUp();
  }

  //Needs to be done each time as some tests manipulate the rules
  private void useDefaultRollingPolicyCirculationRules() {
    log.info("Using rolling loan policy as fallback policy");

    useFallbackPolicies(loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.activeNotice().getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());
  }

  protected void useExampleFixedPolicyCirculationRules() {
    log.info("Using fixed loan policy as fallback policy");

    useFallbackPolicies(loanPoliciesFixture.canCirculateFixed().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.activeNotice().getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());
  }

  protected void useFallbackPolicies(UUID loanPolicyId, UUID requestPolicyId,
    UUID noticePolicyId, UUID overdueFinePolicyId, UUID lostItemFeePolicyId) {

    circulationRulesFixture.updateCirculationRules(loanPolicyId, requestPolicyId,
      noticePolicyId, overdueFinePolicyId, lostItemFeePolicyId);

    warmUpApplyEndpoint();
  }


  /**
   * This method uses the loan policy, allowAllRequestPolicy request policy,
   * inactiveNotice notice policy, facultyStandard overdue fine policy from
   * the loanPolicyBuilder.
   * @param loanPolicyBuilder - loan policy builder.
   */
  protected void setFallbackPolicies(LoanPolicyBuilder loanPolicyBuilder) {
    final IndividualResource loanPolicy = loanPoliciesFixture.create(
      loanPolicyBuilder);

    useFallbackPolicies(loanPolicy.getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.inactiveNotice().getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());
  }

  /**
   * This method uses the loan policy, allowAllRequestPolicy request policy,
   * activeNotice notice policy, facultyStandard overdue fine policy from
   * the loanPolicyBuilder.
   * @param loanPolicyBuilder - loan policy builder.
   */
  protected void useWithActiveNotice(LoanPolicyBuilder loanPolicyBuilder) {
    useFallbackPolicies(loanPoliciesFixture.create(loanPolicyBuilder).getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.activeNotice().getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());
  }

  /**
   * This method uses the loan policy, allowAllRequestPolicy request policy,
   * activeNotice notice policy, facultyStandard overdue fine policy from
   * the loanPolicyBuilder.
   * @param loanPolicyBuilder - loan policy builder.
   */
  protected void use(LoanPolicyBuilder loanPolicyBuilder) {
    useFallbackPolicies(loanPolicyClient.create(loanPolicyBuilder).getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.activeNotice().getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());
  }

  /**
   * This method uses the loan policy, allowAllRequestPolicy request policy,
   * activeNotice notice policy, facultyStandard overdue fine policy from
   * the loanPolicyBuilder and noticePolicyBuilder.
   * @param loanPolicyBuilder - loan policy builder.
   */
  protected void use(LoanPolicyBuilder loanPolicyBuilder,
    NoticePolicyBuilder noticePolicyBuilder) {

    useFallbackPolicies(loanPoliciesFixture.create(loanPolicyBuilder).getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicyBuilder).getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());
  }

  /**
   * This method uses notice policy, canCirculateRolling loan policy,
   * allowAllRequestPolicy request policy,
   * facultyStandard overdue fine policy from
   * the loanPolicyBuilder.
   * @param noticePolicy - notice policy.
   */
  protected void use(NoticePolicyBuilder noticePolicy) {
    useFallbackPolicies(loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicy).getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());
  }

  /**
   * This method uses notice policy, canCirculateRolling loan policy,
   * allowAllRequestPolicy request policy, facultyStandard overdue fine policy from
   * the loanPolicyBuilder.
   * @param noticePolicy - notice policy.
   */
  protected void useWithPaging(NoticePolicyBuilder noticePolicy) {
    useFallbackPolicies(loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.pageRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicy).getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());
  }

  protected void warmUpApplyEndpoint() {
    final URL loanPolicyRulesEndpoint = circulationRulesUrl("/loan-policy");

    final List<QueryStringParameter> parameters = new ArrayList<>();

    parameters.add(namedParameter("item_type_id", UUID.randomUUID().toString()));
    parameters.add(namedParameter("loan_type_id", UUID.randomUUID().toString()));
    parameters.add(namedParameter("patron_type_id", UUID.randomUUID().toString()));
    parameters.add(namedParameter("location_id",
      locationsFixture.mezzanineDisplayCase().getId().toString()));

    restAssuredClient.get(loanPolicyRulesEndpoint, parameters, 200,
      "warm-up-circulation-rules");
  }

  private static void deleteOftenCreatedRecords() {
    ResourceClient.forRequests().deleteAll();
    ResourceClient.forLoans().deleteAll();

    ResourceClient.forItems().deleteAll();
    ResourceClient.forHoldings().deleteAll();
    ResourceClient.forInstances().deleteAll();

    ResourceClient.forUsers().deleteAllIndividually();

    ResourceClient.forAccounts().deleteAll();
  }

  private static void deleteAllRecords() {
    ResourceClient.forRequests().deleteAll();
    ResourceClient.forLoans().deleteAll();

    ResourceClient.forItems().deleteAll();
    ResourceClient.forHoldings().deleteAll();
    ResourceClient.forInstances().deleteAll();

    ResourceClient.forLoanPolicies().deleteAllIndividually();
    ResourceClient.forFixedDueDateSchedules().deleteAllIndividually();

    ResourceClient.forMaterialTypes().deleteAllIndividually();
    ResourceClient.forLoanTypes().deleteAllIndividually();

    ResourceClient.forUsers().deleteAllIndividually();

    ResourceClient.forPatronGroups().deleteAllIndividually();
    ResourceClient.forAddressTypes().deleteAllIndividually();

    ResourceClient.forMaterialTypes().deleteAllIndividually();
    ResourceClient.forLoanTypes().deleteAllIndividually();
    ResourceClient.forLocations().deleteAllIndividually();
    ResourceClient.forServicePoints().deleteAllIndividually();
    ResourceClient.forContributorNameTypes().deleteAllIndividually();
    ResourceClient.forInstanceTypes().deleteAllIndividually();
    ResourceClient.forCancellationReasons().deleteAllIndividually();
  }

  protected void loanHasFeeFinesProperties(JsonObject loan,
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

  protected void setInvalidLoanPolicyReferenceInRules(
    String invalidLoanPolicyReference) {

    circulationRulesFixture.updateCirculationRules(
      circulationRulesFixture.soleFallbackPolicyRule(invalidLoanPolicyReference,
        requestPoliciesFixture.allowAllRequestPolicy().getId().toString(),
        noticePoliciesFixture.inactiveNotice().getId().toString(),
        overdueFinePoliciesFixture.facultyStandard().getId().toString(),
        lostItemFeePoliciesFixture.facultyStandard().getId().toString()));
  }

  protected void setInvalidNoticePolicyReferenceInRules(
    String invalidNoticePolicyReference) {

    circulationRulesFixture.updateCirculationRules(
      circulationRulesFixture.soleFallbackPolicyRule(
        loanPoliciesFixture.canCirculateRolling().getId().toString(),
        requestPoliciesFixture.allowAllRequestPolicy().getId().toString(),
        invalidNoticePolicyReference,
        overdueFinePoliciesFixture.facultyStandard().getId().toString(),
        lostItemFeePoliciesFixture.facultyStandard().getId().toString()));
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
