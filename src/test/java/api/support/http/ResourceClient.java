package api.support.http;

import static api.support.APITestContext.getOkapiHeadersFromContext;
import static api.support.http.CqlQuery.noQuery;
import static api.support.http.Limit.limit;
import static api.support.http.Limit.noLimit;
import static api.support.http.Offset.noOffset;
import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static org.folio.circulation.support.StreamToListMapper.toList;
import static org.folio.circulation.support.json.JsonObjectArrayPropertyFetcher.toStream;

import java.net.URL;
import java.util.List;
import java.util.UUID;

import org.folio.circulation.support.http.client.Response;

import api.support.MultipleJsonRecords;
import api.support.RestAssuredClient;
import api.support.builders.Builder;
import io.vertx.core.json.JsonObject;

public class ResourceClient {
  private final RestAssuredClient restAssuredClient;
  private final UrlMaker urlMaker;
  private final String collectionArrayPropertyName;

  public static ResourceClient forItems() {
    return new ResourceClient(InterfaceUrls::itemsStorageUrl, "items");
  }

  public static ResourceClient forHoldings() {
    return new ResourceClient(InterfaceUrls::holdingsStorageUrl, "holdingsRecords");
  }

  public static ResourceClient forInstances() {
    return new ResourceClient(InterfaceUrls::instancesStorageUrl, "instances");
  }

  public static ResourceClient forRequests() {
    return new ResourceClient(InterfaceUrls::requestsUrl, "requests");
  }

  public static ResourceClient forRequestReport() {
    return new ResourceClient(InterfaceUrls::requestReportUrl, "requestReport");
  }

  public static ResourceClient forItemsInTransitReport() {
    return new ResourceClient(InterfaceUrls::itemsInTransitReportUrl, "items");
  }

  public static ResourceClient forItemsLostRequiringActualCosts() {
    return new ResourceClient(InterfaceUrls::itemsLostRequringActualCostsUrl, "items");
  }

  public static ResourceClient forPickSlips() {
    return new ResourceClient(InterfaceUrls::pickSlipsUrl, "pickSlips");
  }

  public static ResourceClient forLoans() {
    return new ResourceClient(InterfaceUrls::loansUrl, "loans");
  }

  public static ResourceClient forLoanHistoryStorage() {
    return new ResourceClient(InterfaceUrls::loanHistoryStorageUrl, "loansHistory");
  }

  public static ResourceClient forAccounts() {
    return new ResourceClient(InterfaceUrls::accountsUrl, "accounts");
  }

  public static ResourceClient forFeeFineActions() {
    return new ResourceClient(InterfaceUrls::feeFineActionsUrl, "feefineactions");
  }

  public static ResourceClient forFeeFineOwners() {
    return new ResourceClient(InterfaceUrls::feeFineOwnersUrl, "owners");
  }

  public static ResourceClient forFeeFines() {
    return new ResourceClient(InterfaceUrls::feeFinesUrl, "feefines");
  }

  public static ResourceClient forNotes() {
    return new ResourceClient(InterfaceUrls::notesUrl, "notes");
  }

  public static ResourceClient forNoteTypes() {
    return new ResourceClient(InterfaceUrls::noteTypesUrl, "noteTypes");
  }

  public static ResourceClient forLoanPolicies() {
    return new ResourceClient(InterfaceUrls::loanPoliciesStorageUrl,
      "loanPolicies");
  }

  public static ResourceClient forRequestPolicies() {
    return new ResourceClient(InterfaceUrls::requestPoliciesStorageUrl,
      "requestPolicies");
  }

  public static ResourceClient forNoticePolicies() {
    return new ResourceClient(InterfaceUrls::noticePoliciesStorageUrl,
      "patronNoticePolicies");
  }

  public static ResourceClient forOverdueFinePolicies() {
    return new ResourceClient(InterfaceUrls::overdueFinesPoliciesStorageUrl,
      "overdueFinePolicies");
  }

  public static ResourceClient forLostItemFeePolicies() {
    return new ResourceClient(InterfaceUrls::lostItemFeesPoliciesStorageUrl,
      "lostItemFeePolicies");
  }

  public static ResourceClient forFixedDueDateSchedules() {
    return new ResourceClient(InterfaceUrls::fixedDueDateSchedulesStorageUrl,
      "fixedDueDateSchedules");
  }

  public static ResourceClient forUsers() {
    return new ResourceClient(InterfaceUrls::usersUrl,
      "users");
  }

  public static ResourceClient forProxyRelationships() {
    return new ResourceClient(InterfaceUrls::proxyRelationshipsUrl,
        "proxiesFor");
  }

  public static ResourceClient forPatronGroups() {
    return new ResourceClient(InterfaceUrls::patronGroupsStorageUrl,
        "usergroups");
  }

  public static ResourceClient forAddressTypes() {
    return new ResourceClient(InterfaceUrls::addressTypesUrl,
        "addressTypes");
  }

  public static ResourceClient forLoansStorage() {
    return new ResourceClient(InterfaceUrls::loansStorageUrl,
        "loans");
  }

  public static ResourceClient forRequestsStorage() {
    return new ResourceClient(InterfaceUrls::requestStorageUrl,
        "requests");
  }

  public static ResourceClient forMaterialTypes() {
    return new ResourceClient(InterfaceUrls::materialTypesStorageUrl,
      "mtypes");
  }

  public static ResourceClient forUserManualBlocks() {
    return new ResourceClient(subPath ->
      InterfaceUrls.userManualBlocksStorageUrl(), "manualblocks");
  }

  public static ResourceClient forAutomatedPatronBlocks() {
    return new ResourceClient(subPath ->
      InterfaceUrls.automatedPatronBlocksStorageUrl(), "automatedPatronBlocks");
  }

  public static ResourceClient forLoanTypes() {
    return new ResourceClient(InterfaceUrls::loanTypesStorageUrl,
        "loantypes");
  }

  public static ResourceClient forInstanceTypes() {
    return new ResourceClient(InterfaceUrls::instanceTypesStorageUrl,
        "instanceTypes");
  }

  public static ResourceClient forContributorNameTypes() {
    return new ResourceClient(InterfaceUrls::contributorNameTypesStorageUrl,
      "contributorNameTypes");
  }

  public static ResourceClient forInstitutions() {
    return new ResourceClient(InterfaceUrls::institutionsStorageUrl,
        "locinsts");
  }

  public static ResourceClient forCampuses() {
    return new ResourceClient(InterfaceUrls::campusesStorageUrl,
        "loccamps");
  }

  public static ResourceClient forLibraries() {
    return new ResourceClient(InterfaceUrls::librariesStorageUrl, "loclibs");
  }

  public static ResourceClient forLocations() {
    return new ResourceClient(InterfaceUrls::locationsStorageUrl, "locations");
  }

  public static ResourceClient forCancellationReasons() {
    return new ResourceClient(InterfaceUrls::cancellationReasonsStorageUrl,
        "cancellationReasons");
  }

  public static ResourceClient forServicePoints() {
    return new ResourceClient(InterfaceUrls::servicePointsStorageUrl,
        "servicepoints");
  }

  public static ResourceClient forPatronNotices() {
    return new ResourceClient(InterfaceUrls::patronNoticesUrl,
        "patronnotices");
  }

  public static ResourceClient forScheduledNotices() {
    return new ResourceClient(InterfaceUrls::scheduledNoticesUrl,
        "scheduledNotices");
  }

  public static ResourceClient forPatronSessionRecords() {
    return new ResourceClient(InterfaceUrls::patronActionSessionsUrl,
        "patronActionSessions");
  }

  public static ResourceClient forExpiredSessions() {
    return new ResourceClient(InterfaceUrls::patronExpiredSessionsUrl,
        "expired session records");
  }

  public static ResourceClient forConfiguration() {
    return new ResourceClient(InterfaceUrls::configurationUrl, "configs");
  }

  public static ResourceClient forTemplates() {
    return new ResourceClient(InterfaceUrls::templateUrl, "templates");
  }

  public static ResourceClient forIdentifierTypes() {
    return new ResourceClient(InterfaceUrls::identifierTypesUrl,
      "identifierTypes");
  }

  public static ResourceClient forCheckInStorage() {
    return new ResourceClient(InterfaceUrls::checkInStorage,
      "checkIns");
  }

  public static ResourceClient forTenantStorage() {
    return new ResourceClient(InterfaceUrls::tenantStorage, "");
  }

  private ResourceClient(UrlMaker urlMaker, String collectionArrayPropertyName) {
    this.urlMaker = urlMaker;
    this.collectionArrayPropertyName = collectionArrayPropertyName;
    restAssuredClient = new RestAssuredClient(getOkapiHeadersFromContext());
  }

  public Response attemptCreate(Builder builder) {
    return attemptCreate(builder.create());
  }

  public Response attemptCreate(JsonObject representation) {

    return restAssuredClient.post(representation, rootUrl(),
        "attempt-create-record");
  }

  public IndividualResource create(Builder builder) {
    return create(builder.create());
  }

  public IndividualResource create(JsonObject representation) {

    return  new IndividualResource(restAssuredClient.post(representation,
      rootUrl(), 201, "create-record"));
  }

  public Response attemptCreateAtSpecificLocation(Builder builder) {

    final JsonObject representation = builder.create();
    final URL location = recordUrl(representation.getString("id"));

    return restAssuredClient.put(representation, location,
      "attempt-create-record-at-specific-location");
  }

  public IndividualResource createAtSpecificLocation(Builder builder) {

    final JsonObject representation = builder.create();
    final URL location = recordUrl(representation.getString("id"));

    restAssuredClient.put(representation, location, HTTP_NO_CONTENT,
      "create-record-at-specific-location");

    return get(location);
  }

  public Response attemptReplace(UUID id, Builder builder) {

    return attemptReplace(id, builder.create());
  }

  public Response attemptReplace(UUID id, JsonObject representation) {

    return restAssuredClient.put(representation, recordUrl(id),
      "attempt-replace-record");
  }

  public void replace(UUID id, Builder builder) {
    replace(id, builder.create());
  }

  public void replace(UUID id, JsonObject representation) {

    restAssuredClient.put(representation, recordUrl(id), HTTP_NO_CONTENT,
      "create-record-at-specific-location");
  }

  public Response getById(UUID id) {
    return restAssuredClient.get(recordUrl(id), "get-record");
  }

  public IndividualResource get(IndividualResource record) {

    return get(record.getId());
  }

  public IndividualResource get(UUID id) {
    return get(recordUrl(id));
  }

  public IndividualResource get(URL url) {
    return new IndividualResource(restAssuredClient.get(url, 200, "get-record"));
  }

  public MultipleJsonRecords getMany(CqlQuery query) {
    Response response = restAssuredClient.get(urlMaker.combine(""), query,
      noLimit(), noOffset(), 200, "get-many");

    return MultipleJsonRecords.multipleRecordsFrom(response, collectionArrayPropertyName);
  }

  public Response attemptGet(IndividualResource resource) {

    return getById(resource.getId());
  }

  public void delete(UUID id) {
    restAssuredClient.delete(recordUrl(id), HTTP_NO_CONTENT, "delete-record");
  }

  public void delete(IndividualResource resource) {
    delete(resource.getId());
  }

  public void deleteAll() {
    restAssuredClient.delete(rootUrl(), HTTP_NO_CONTENT, "delete-all-records");
  }

  public void deleteAllIndividually() {
    for (JsonObject record : getAll()) {
      delete(UUID.fromString(record.getString("id")));
    }
  }

  //TODO: Replace return value with MultipleJsonRecords
  public List<JsonObject> getAll() {
    return toList(toStream(restAssuredClient
      .get(rootUrl(), noQuery(), limit(1000), noOffset(), 200, "get-all")
      .getJson(), collectionArrayPropertyName));
  }

  private URL recordUrl(Object id) {
    return urlMaker.combine(String.format("/%s", id));
  }

  private URL rootUrl() {
    return urlMaker.combine("");
  }

  @FunctionalInterface
  public interface UrlMaker {
    URL combine(String subPath);
  }
}
