package org.folio.circulation.support;

import java.net.MalformedURLException;

import org.folio.circulation.rules.CirculationRulesProcessor;
import org.folio.circulation.services.PubSubPublishingService;
import org.folio.circulation.support.http.client.IncludeRoutingServicePoints;
import org.folio.circulation.support.http.client.OkapiHttpClient;
import org.folio.circulation.support.http.client.QueryParameter;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.core.http.HttpClient;

public class Clients {
  private final CollectionResourceClient requestsStorageClient;
  private final CollectionResourceClient requestsBatchStorageClient;
  private final CollectionResourceClient cancellationReasonStorageClient;
  private final CollectionResourceClient itemsStorageClient;
  private final CollectionResourceClient holdingsStorageClient;
  private final CollectionResourceClient instancesStorageClient;
  private final CollectionResourceClient identifierTypesStorageClient;
  private final CollectionResourceClient usersStorageClient;
  private final CollectionResourceClient addressTypesStorageClient;
  private final CollectionResourceClient loansStorageClient;
  private final CollectionResourceClient loansHistoryStorageClient;
  private final CollectionResourceClient locationsStorageClient;
  private final CollectionResourceClient institutionsStorageClient;
  private final CollectionResourceClient campusesStorageClient;
  private final CollectionResourceClient librariesStorageClient;
  private final CollectionResourceClient materialTypesStorageClient;
  private final CollectionResourceClient loanTypesStorageClient;
  private final GetManyRecordsClient proxiesForClient;
  private final CollectionResourceClient loanPoliciesStorageClient;
  private final CollectionResourceClient overdueFinesPoliciesPoliciesStorageClient;
  private final CollectionResourceClient lostItemPoliciesStorageClient;
  private final GetManyRecordsClient fixedDueDateSchedulesStorageClient;
  private final CirculationRulesClient circulationLoanRulesClient;
  private final CirculationRulesClient circulationOverdueFinesRulesClient;
  private final CirculationRulesClient circulationLostItemRulesClient;
  private final CirculationRulesClient circulationRequestRulesClient;
  private final CirculationRulesClient circulationNoticeRulesClient;
  private final CollectionResourceClient circulationRulesStorageClient;
  private final CollectionResourceClient requestPoliciesStorageClient;
  private final CollectionResourceClient servicePointsStorageClient;
  private final CollectionResourceClient routingServicePointsStorageClient;
  private final CollectionResourceClient calendarStorageClient;
  private final CollectionResourceClient patronGroupsStorageClient;
  private final CollectionResourceClient patronNoticePolicesStorageClient;
  private final CollectionResourceClient patronNoticeClient;
  private final GetManyRecordsClient configurationStorageClient;
  private final CollectionResourceClient scheduledNoticesStorageClient;
  private final CollectionResourceClient accountsStorageClient;
  private final CollectionResourceClient feeFineActionsStorageClient;
  private final CollectionResourceClient feeFineOwnerStorageClient;
  private final CollectionResourceClient feeFineStorageClient;
  private final CollectionResourceClient anonymizeStorageLoansClient;
  private final CollectionResourceClient patronActionSessionsStorageClient;
  private final CollectionResourceClient patronExpiredSessionsStorageClient;
  private final GetManyRecordsClient userManualBlocksStorageClient;
  private final CollectionResourceClient noticeTemplatesClient;
  private final CollectionResourceClient checkInStorageClient;
  private final CollectionResourceClient automatedPatronBlocksClient;
  private final CollectionResourceClient notesClient;
  private final CollectionResourceClient noteTypesClient;
  private final PubSubPublishingService pubSubPublishingService;
  private final CirculationRulesProcessor circulationRulesProcessor;
  private final CollectionResourceClient accountsRefundClient;
  private final CollectionResourceClient accountsCancelClient;
  private final CollectionResourceClient actualCostRecordsStorageClient;
  private final CollectionResourceClient actualCostFeeFineCancelClient;
  private final CollectionResourceClient departmentClient;
  private final CollectionResourceClient checkOutLockStorageClient;
  private final CollectionResourceClient circulationItemClient;
  private final CollectionResourceClient searchClient;
  private final GetManyRecordsClient settingsStorageClient;
  private final CollectionResourceClient circulationSettingsStorageClient;
  private final CollectionResourceClient printEventsStorageClient;


  public static Clients create(WebContext context, HttpClient httpClient) {
    return new Clients(context.createHttpClient(httpClient), context);
  }

  public static Clients create(WebContext context, HttpClient httpClient, String tenantId) {
    return new Clients(context.createHttpClient(httpClient, tenantId), context);
  }

  private Clients(OkapiHttpClient client, WebContext context) {
    try {
      requestsStorageClient = createRequestsStorageClient(client, context);
      requestsBatchStorageClient = createRequestsBatchStorageClient(client, context);
      cancellationReasonStorageClient = createCancellationReasonStorageClient(client, context);
      itemsStorageClient = createItemsStorageClient(client, context);
      holdingsStorageClient = createHoldingsStorageClient(client, context);
      instancesStorageClient = createInstanceStorageClient(client, context);
      identifierTypesStorageClient = createIdentifierTypesStorageClient(client, context);
      usersStorageClient = createUsersStorageClient(client, context);
      addressTypesStorageClient = createAddressTypesStorageClient(client, context);
      loansStorageClient = createLoansStorageClient(client, context);
      loansHistoryStorageClient = createLoansHistoryStorageClient(client, context);
      overdueFinesPoliciesPoliciesStorageClient = createOverdueFinesPoliciesStorageClient(client, context);
      lostItemPoliciesStorageClient = createLostItemPoliciesStorageClient(client, context);
      locationsStorageClient = createLocationsStorageClient(client, context);
      anonymizeStorageLoansClient = createAnonymizeStorageLoansClient(client, context);
      institutionsStorageClient = createInstitutionsStorageClient(client, context);
      campusesStorageClient = createCampusesStorageClient(client, context);
      librariesStorageClient = createLibrariesStorageClient(client, context);
      materialTypesStorageClient = createMaterialTypesStorageClient(client, context);
      loanTypesStorageClient = createLoanTypesStorageClient(client, context);
      proxiesForClient = createProxyUsersStorageClient(client, context);
      circulationLoanRulesClient = createCirculationLoanRulesClient(client, context);
      circulationRequestRulesClient = createCirculationRequestRulesClient(client, context);
      circulationNoticeRulesClient = createCirculationNoticeRulesClient(client, context);
      circulationOverdueFinesRulesClient = createCirculationOverdueFinesRulesClient(client, context);
      circulationLostItemRulesClient = createCirculationLostItemRulesClient(client, context);
      circulationRulesStorageClient = createCirculationRulesStorageClient(client, context);
      loanPoliciesStorageClient = createLoanPoliciesStorageClient(client, context);
      requestPoliciesStorageClient = createRequestPoliciesStorageClient(client, context);
      fixedDueDateSchedulesStorageClient = createFixedDueDateSchedulesStorageClient(client, context);
      servicePointsStorageClient = createServicePointsStorageClient(client, context);
      routingServicePointsStorageClient = createServicePointsStorageWithCustomParam(client,
        context, IncludeRoutingServicePoints.enabled());
      patronGroupsStorageClient = createPatronGroupsStorageClient(client, context);
      calendarStorageClient = createCalendarStorageClient(client, context);
      patronNoticePolicesStorageClient = createPatronNoticePolicesStorageClient(client, context);
      patronNoticeClient = createPatronNoticeClient(client, context);
      configurationStorageClient = createConfigurationStorageClient(client, context);
      scheduledNoticesStorageClient = createScheduledNoticesStorageClient(client, context);
      accountsStorageClient = createAccountsStorageClient(client, context);
      feeFineActionsStorageClient = createFeeFineActionsStorageClient(client,context);
      feeFineOwnerStorageClient = createFeeFineOwnerStorageClient(client,context);
      feeFineStorageClient = createFeeFineStorageClient(client,context);
      patronActionSessionsStorageClient = createPatronActionSessionsStorageClient(client, context);
      patronExpiredSessionsStorageClient = createPatronExpiredSessionsStorageClient(client, context);
      userManualBlocksStorageClient = createUserManualBlocksStorageClient(client, context);
      noticeTemplatesClient = createNoticeTemplatesClient(client, context);
      checkInStorageClient = createCheckInStorageClient(client, context);
      automatedPatronBlocksClient = createAutomatedPatronBlocksClient(client, context);
      notesClient = createNotesClient(client, context);
      noteTypesClient = createNoteTypesClient(client, context);
      pubSubPublishingService = createPubSubPublishingService(context);
      circulationRulesProcessor = new CirculationRulesProcessor(context.getTenantId(),
        circulationRulesStorageClient, locationsStorageClient);
      accountsRefundClient = createAccountsRefundClient(client, context);
      accountsCancelClient = createAccountsCancelClient(client, context);
      actualCostRecordsStorageClient = createActualCostRecordClient(client, context);
      actualCostFeeFineCancelClient = createActualCostFeeFineCancelClient(client, context);
      departmentClient = createDepartmentClient(client, context);
      checkOutLockStorageClient = createCheckoutLockClient(client, context);
      settingsStorageClient = createSettingsStorageClient(client, context);
      circulationItemClient = createCirculationItemClient(client, context);
      searchClient = createSearchClient(client, context);
      circulationSettingsStorageClient = createCirculationSettingsStorageClient(client, context);
      printEventsStorageClient = createPrintEventsStorageClient(client, context);

    }
    catch(MalformedURLException e) {
      throw new InvalidOkapiLocationException(context.getOkapiLocation(), e);
    }
  }

  public CollectionResourceClient requestsStorage() {
    return requestsStorageClient;
  }

  public CollectionResourceClient requestsBatchStorage() {
    return requestsBatchStorageClient;
  }

  public CollectionResourceClient cancellationReasonStorage() {
    return cancellationReasonStorageClient;
  }

  public CollectionResourceClient requestPoliciesStorage() {
    return requestPoliciesStorageClient;
  }

  public CollectionResourceClient itemsStorage() {
    return itemsStorageClient;
  }

  public CollectionResourceClient holdingsStorage() {
    return holdingsStorageClient;
  }

  public CollectionResourceClient instancesStorage() {
    return instancesStorageClient;
  }

  public CollectionResourceClient identifierTypesStorage() {
    return identifierTypesStorageClient;
  }

  public CollectionResourceClient usersStorage() {
    return usersStorageClient;
  }

  public CollectionResourceClient addressTypesStorage() {
    return addressTypesStorageClient;
  }

  public CollectionResourceClient loansStorage() {
    return loansStorageClient;
  }

  public CollectionResourceClient loansHistoryStorageClient() {
    return loansHistoryStorageClient;
  }

  public CollectionResourceClient anonymizeStorageLoansClient() {
    return anonymizeStorageLoansClient;
  }

  public CollectionResourceClient locationsStorage() {
    return locationsStorageClient;
  }

  public CollectionResourceClient institutionsStorage() {
    return institutionsStorageClient;
  }

  public CollectionResourceClient campusesStorage() {
    return campusesStorageClient;
  }

  public CollectionResourceClient librariesStorage() {
    return librariesStorageClient;
  }

  public CollectionResourceClient materialTypesStorage() {
    return materialTypesStorageClient;
  }

  public CollectionResourceClient loanTypesStorage() {
    return loanTypesStorageClient;
  }

  public CollectionResourceClient loanPoliciesStorage() {
    return loanPoliciesStorageClient;
  }

  public CollectionResourceClient overdueFinesPoliciesStorage() {
    return overdueFinesPoliciesPoliciesStorageClient;
  }

  public CollectionResourceClient lostItemPoliciesStorage() {
    return lostItemPoliciesStorageClient;
  }

  public GetManyRecordsClient fixedDueDateSchedules() {
    return fixedDueDateSchedulesStorageClient;
  }

  public CollectionResourceClient servicePointsStorage() {
    return servicePointsStorageClient;
  }

  public CollectionResourceClient routingServicePointsStorage() {
    return routingServicePointsStorageClient;
  }

  public CollectionResourceClient patronGroupsStorage() {
    return patronGroupsStorageClient;
  }

  public CollectionResourceClient actualCostRecordsStorage() {
    return actualCostRecordsStorageClient;
  }

  public CollectionResourceClient calendarStorageClient() {
    return calendarStorageClient;
  }

  public GetManyRecordsClient configurationStorageClient() {
    return configurationStorageClient;
  }

  public GetManyRecordsClient userProxies() {
    return proxiesForClient;
  }

  public CirculationRulesClient circulationLoanRules() {
    return circulationLoanRulesClient;
  }

  public CirculationRulesClient circulationOverdueFineRules() {
    return circulationOverdueFinesRulesClient;
  }

  public CirculationRulesClient circulationLostItemRules() {
    return circulationLostItemRulesClient;
  }

  public CirculationRulesClient circulationRequestRules(){
    return circulationRequestRulesClient;
  }

  public CirculationRulesClient circulationNoticeRules(){
    return circulationNoticeRulesClient;
  }

  public CollectionResourceClient circulationRulesStorage() {
    return circulationRulesStorageClient;
  }

  public CollectionResourceClient patronNoticePolicesStorageClient() {
    return patronNoticePolicesStorageClient;
  }

  public CollectionResourceClient patronNoticeClient() {
    return patronNoticeClient;
  }

  public CollectionResourceClient scheduledNoticesStorageClient() {
    return scheduledNoticesStorageClient;
  }

  public CollectionResourceClient accountsStorageClient() {
    return accountsStorageClient;
  }

  public CollectionResourceClient feeFineActionsStorageClient() {
    return feeFineActionsStorageClient;
  }

  public CollectionResourceClient feeFineOwnerStorageClient() {
    return feeFineOwnerStorageClient;
  }

  public CollectionResourceClient feeFineStorageClient() {
    return feeFineStorageClient;
  }

  public CollectionResourceClient patronActionSessionsStorageClient() {
    return patronActionSessionsStorageClient;
  }

  public CollectionResourceClient patronExpiredSessionsStorageClient() {
    return patronExpiredSessionsStorageClient;
  }

  public GetManyRecordsClient userManualBlocksStorageClient() {
    return userManualBlocksStorageClient;
  }

  public CollectionResourceClient checkInStorageClient() {
    return checkInStorageClient;
  }

  public CollectionResourceClient automatedPatronBlocksClient() {
    return automatedPatronBlocksClient;
  }

  public CollectionResourceClient notesClient() {
    return notesClient;
  }

  public CollectionResourceClient noteTypesClient() {
    return noteTypesClient;
  }

  public CirculationRulesProcessor circulationRulesProcessor() {
    return circulationRulesProcessor;
  }

  public PubSubPublishingService pubSubPublishingService() {
    return pubSubPublishingService;
  }

  public CollectionResourceClient accountsRefundClient() {
    return accountsRefundClient;
  }

  public CollectionResourceClient accountsCancelClient() {
    return accountsCancelClient;
  }

  public CollectionResourceClient actualCostFeeFineCancelClient() {
    return actualCostFeeFineCancelClient;
  }

  public CollectionResourceClient departmentClient() {
    return departmentClient;
  }

  public CollectionResourceClient checkOutLockClient() {
    return checkOutLockStorageClient;
  }

  public GetManyRecordsClient settingsStorageClient() {
    return settingsStorageClient;
  }

  public CollectionResourceClient circulationItemClient() {
    return circulationItemClient;
  }

  public CollectionResourceClient searchClient() {
    return searchClient;
  }

  public CollectionResourceClient circulationSettingsStorageClient() {
    return circulationSettingsStorageClient;
  }

  public CollectionResourceClient printEventsStorageClient() {
    return printEventsStorageClient;
  }

  private static CollectionResourceClient getCollectionResourceClient(
    OkapiHttpClient client, WebContext context,
    String path)
    throws MalformedURLException {

    return new CollectionResourceClient(client, context.getOkapiBasedUrl(path));
  }

  private static CollectionResourceClient getCollectionResourceClientWithCustomParam(
    OkapiHttpClient client, WebContext context, String path, QueryParameter customParam)
    throws MalformedURLException {

    return new CustomParamCollectionResourceClient(client, context.getOkapiBasedUrl(path),
      customParam);
  }

  public CollectionResourceClient noticeTemplatesClient() {
    return noticeTemplatesClient;
  }

  private static CirculationRulesClient createCirculationLoanRulesClient(
    OkapiHttpClient client, WebContext context)
    throws MalformedURLException {

    return new CirculationRulesClient(client, context,
      "/circulation/rules/loan-policy");
  }

  private static CirculationRulesClient createCirculationOverdueFinesRulesClient(
    OkapiHttpClient client, WebContext context)
    throws MalformedURLException {

    return new CirculationRulesClient(client, context,
      "/circulation/rules/overdue-fine-policy");
  }

  private static CirculationRulesClient createCirculationLostItemRulesClient(
    OkapiHttpClient client, WebContext context)
    throws MalformedURLException {

    return new CirculationRulesClient(client, context,
      "/circulation/rules/lost-item-policy");
  }

  private static CirculationRulesClient createCirculationRequestRulesClient(
    OkapiHttpClient client, WebContext context)
    throws MalformedURLException {

    return new CirculationRulesClient(client, context,
      "/circulation/rules/request-policy");
  }

  private static CirculationRulesClient createCirculationNoticeRulesClient(
    OkapiHttpClient client, WebContext context)
    throws MalformedURLException {

    return new CirculationRulesClient(client, context,
      "/circulation/rules/notice-policy");
  }

  private static CollectionResourceClient createRequestsStorageClient(
    OkapiHttpClient client, WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context,
      "/request-storage/requests");
  }

  private static CollectionResourceClient createRequestsBatchStorageClient(
    OkapiHttpClient client, WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context,
      "/request-storage-batch/requests");
  }

  private static CollectionResourceClient createCancellationReasonStorageClient(
    OkapiHttpClient client, WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context,
      "/cancellation-reason-storage/cancellation-reasons");
  }

  private static CollectionResourceClient createItemsStorageClient(
    OkapiHttpClient client, WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context, "/item-storage/items");
  }

  private static CollectionResourceClient createHoldingsStorageClient(
    OkapiHttpClient client, WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context,
      "/holdings-storage/holdings");
  }

  private static CollectionResourceClient createInstanceStorageClient(
    OkapiHttpClient client, WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context,
      "/instance-storage/instances");
  }

  private static CollectionResourceClient createIdentifierTypesStorageClient(
    OkapiHttpClient client, WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context,
      "/identifier-types");
  }

  private static CollectionResourceClient createUsersStorageClient(
    OkapiHttpClient client, WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context, "/users");
  }

  private static CollectionResourceClient createAddressTypesStorageClient(
    OkapiHttpClient client, WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context, "/addresstypes");
  }

  private static CollectionResourceClient createLoansStorageClient(
    OkapiHttpClient client, WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context, "/loan-storage/loans");
  }

  private static CollectionResourceClient createLoansHistoryStorageClient(
    OkapiHttpClient client, WebContext context) throws MalformedURLException {

    return getCollectionResourceClient(client, context, "/loan-storage/loan-history");
  }

  private static CollectionResourceClient createAnonymizeStorageLoansClient(
    OkapiHttpClient client, WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context,
      "/anonymize-storage-loans");
  }

  private static CollectionResourceClient createLocationsStorageClient(
    OkapiHttpClient client, WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context, "/locations");
  }

  private static CollectionResourceClient createInstitutionsStorageClient(
    OkapiHttpClient client, WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context,
      "/location-units/institutions");
  }

  private static CollectionResourceClient createCampusesStorageClient(
    OkapiHttpClient client, WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context,
      "/location-units/campuses");
  }

  private static CollectionResourceClient createLibrariesStorageClient(
    OkapiHttpClient client, WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context,
      "/location-units/libraries");
  }

  private GetManyRecordsClient createProxyUsersStorageClient(
    OkapiHttpClient client, WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context, "/proxiesfor");
  }

  private CollectionResourceClient createMaterialTypesStorageClient(
    OkapiHttpClient client, WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context, "/material-types");
  }

  private CollectionResourceClient createLoanTypesStorageClient(
    OkapiHttpClient client, WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context, "/loan-types");
  }

  private CollectionResourceClient createLoanPoliciesStorageClient(
    OkapiHttpClient client, WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context,
      "/loan-policy-storage/loan-policies");
  }

  private CollectionResourceClient createOverdueFinesPoliciesStorageClient(
    OkapiHttpClient client, WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context,
            "/overdue-fines-policies");
  }

  private CollectionResourceClient createLostItemPoliciesStorageClient(
    OkapiHttpClient client, WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context,
            "/lost-item-fees-policies");
  }

  private CollectionResourceClient createRequestPoliciesStorageClient(
    OkapiHttpClient client, WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context,
      "/request-policy-storage/request-policies");
  }

  private GetManyRecordsClient createFixedDueDateSchedulesStorageClient(
    OkapiHttpClient client, WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context,
      "/fixed-due-date-schedule-storage/fixed-due-date-schedules");
  }


  private CollectionResourceClient createCirculationRulesStorageClient(
    OkapiHttpClient client, WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context,
      "/circulation-rules-storage");
  }

  private CollectionResourceClient createServicePointsStorageClient(
    OkapiHttpClient client, WebContext context)
      throws MalformedURLException {

    return getCollectionResourceClient(client, context, "/service-points");
  }

  private CollectionResourceClient createServicePointsStorageWithCustomParam(
    OkapiHttpClient client, WebContext context, QueryParameter customParam)
      throws MalformedURLException {

    return getCollectionResourceClientWithCustomParam(client, context, "/service-points",
      customParam);
  }

  private CollectionResourceClient createPatronGroupsStorageClient(
    OkapiHttpClient client, WebContext context)
      throws MalformedURLException {

    return getCollectionResourceClient(client, context, "/groups");
  }

  private CollectionResourceClient createCalendarStorageClient(
    OkapiHttpClient client, WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context, "/calendar/dates");
  }

  private CollectionResourceClient createPatronNoticePolicesStorageClient(
    OkapiHttpClient client, WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context,
      "/patron-notice-policy-storage/patron-notice-policies");
  }

  private CollectionResourceClient createPatronNoticeClient(
    OkapiHttpClient client, WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context, "/patron-notice");
  }

  private GetManyRecordsClient createConfigurationStorageClient(
    OkapiHttpClient client, WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context,
      "/configurations/entries");
  }

  private CollectionResourceClient createScheduledNoticesStorageClient(
    OkapiHttpClient client, WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context,
      "/scheduled-notice-storage/scheduled-notices");
  }

  private CollectionResourceClient createAccountsStorageClient(
    OkapiHttpClient client, WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context, "/accounts");
  }

  private CollectionResourceClient createFeeFineActionsStorageClient(
    OkapiHttpClient client, WebContext context)
      throws MalformedURLException {

    return getCollectionResourceClient(client, context, "/feefineactions");
  }

  private CollectionResourceClient createFeeFineOwnerStorageClient(
    OkapiHttpClient client,
    WebContext context)
    throws MalformedURLException {
    return getCollectionResourceClient(client, context, "/owners");
  }

  private CollectionResourceClient createFeeFineStorageClient(
    OkapiHttpClient client,
    WebContext context)
    throws MalformedURLException {
    return getCollectionResourceClient(client, context, "/feefines");
  }


  private CollectionResourceClient createPatronActionSessionsStorageClient(
    OkapiHttpClient client, WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context,
      "/patron-action-session-storage/patron-action-sessions");
  }

  private CollectionResourceClient createPatronExpiredSessionsStorageClient(
    OkapiHttpClient client, WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context,
      "/patron-action-session-storage");
  }

  private static GetManyRecordsClient createUserManualBlocksStorageClient(
    OkapiHttpClient client, WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context, "/manualblocks");
  }

  private CollectionResourceClient createNoticeTemplatesClient(
    OkapiHttpClient client, WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context, "/templates");
  }

  private CollectionResourceClient createCheckInStorageClient(
    OkapiHttpClient client, WebContext context) throws MalformedURLException {

    return getCollectionResourceClient(client, context, "/check-in-storage/check-ins");
  }

  private static CollectionResourceClient createAutomatedPatronBlocksClient(
    OkapiHttpClient client, WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context, "/automated-patron-blocks");
  }

  private CollectionResourceClient createNotesClient(
    OkapiHttpClient client, WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context, "/notes");
  }

  private CollectionResourceClient createNoteTypesClient(
    OkapiHttpClient client, WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context, "/note-types");
  }

  private PubSubPublishingService createPubSubPublishingService(WebContext context) {
    return new PubSubPublishingService(context);
  }

  private CollectionResourceClient createAccountsRefundClient(
    OkapiHttpClient client, WebContext context) throws MalformedURLException {

    return getCollectionResourceClient(client, context, "/accounts/%s/refund");
  }

  private CollectionResourceClient createAccountsCancelClient(
    OkapiHttpClient client, WebContext context) throws MalformedURLException {

    return getCollectionResourceClient(client, context, "/accounts/%s/cancel");
  }

  private CollectionResourceClient createActualCostRecordClient(
    OkapiHttpClient client, WebContext context) throws MalformedURLException {

    return getCollectionResourceClient(client, context, "/actual-cost-record-storage/actual-cost-records");
  }

  private CollectionResourceClient createActualCostFeeFineCancelClient(
    OkapiHttpClient client, WebContext context) throws MalformedURLException {

    return getCollectionResourceClient(client, context, "/actual-cost-fee-fine/cancel");
  }

  private CollectionResourceClient createDepartmentClient(
    OkapiHttpClient client, WebContext context) throws MalformedURLException {

    return getCollectionResourceClient(client, context, "/departments");
  }

  private CollectionResourceClient createCheckoutLockClient(
    OkapiHttpClient client, WebContext context) throws MalformedURLException {

    return  getCollectionResourceClient(client, context, "/check-out-lock-storage");
  }

  private CollectionResourceClient createCirculationItemClient(
    OkapiHttpClient client, WebContext context) throws MalformedURLException {

    return  getCollectionResourceClient(client, context, "/circulation-item");
  }

  private CollectionResourceClient createSearchClient(
    OkapiHttpClient client, WebContext context) throws MalformedURLException {

    return  getCollectionResourceClient(client, context, "/search/instances");
  }

  private CollectionResourceClient createCirculationSettingsStorageClient(
    OkapiHttpClient client, WebContext context) throws MalformedURLException {

    return  getCollectionResourceClient(client, context,
      "/circulation-settings-storage/circulation-settings");
  }

  private CollectionResourceClient createPrintEventsStorageClient(
    OkapiHttpClient client, WebContext context) throws MalformedURLException {

    return  getCollectionResourceClient(client, context,
      "/print-events-storage/print-events-entry");
  }

  private GetManyRecordsClient createSettingsStorageClient(
    OkapiHttpClient client, WebContext context)
    throws MalformedURLException {

    return getCollectionResourceClient(client, context,
      "/settings/entries");
  }

}
