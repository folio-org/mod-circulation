package api.support.http;

import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.lang.invoke.MethodHandles;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.JsonArrayHelper;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.OkapiHttpClient;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.hamcrest.CoreMatchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import api.support.builders.Builder;
import io.vertx.core.json.JsonObject;

public class ResourceClient {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final OkapiHttpClient client;
  private final UrlMaker urlMaker;
  private final String resourceName;
  private final String collectionArrayPropertyName;

  public static ResourceClient forItems(OkapiHttpClient client) {
    return new ResourceClient(client, InterfaceUrls::itemsStorageUrl,
      "items");
  }

  public static ResourceClient forHoldings(OkapiHttpClient client) {
    return new ResourceClient(client, InterfaceUrls::holdingsStorageUrl,
      "holdingsRecords");
  }

  public static ResourceClient forInstances(OkapiHttpClient client) {
    return new ResourceClient(client, InterfaceUrls::instancesStorageUrl,
      "instances");
  }

  public static ResourceClient forRequests(OkapiHttpClient client) {
    return new ResourceClient(client, InterfaceUrls::requestsUrl,
      "requests");
  }

  public static ResourceClient forRequestReport(OkapiHttpClient client) {
    return new ResourceClient(client, InterfaceUrls::requestReportUrl,
      "requestReport");
  }

  public static ResourceClient forItemsInTransitReport(OkapiHttpClient client) {
    return new ResourceClient(client,
      subPath -> InterfaceUrls.itemsInTransitReportUrl(), "items");
  }

  public static ResourceClient forLoans(OkapiHttpClient client) {
    return new ResourceClient(client, InterfaceUrls::loansUrl,
      "loans");
  }

  public static ResourceClient forAccounts(OkapiHttpClient client) {
    return new ResourceClient(client, InterfaceUrls::accountsUrl,
      "accounts");
  }

  public static ResourceClient forFeeFineActions(OkapiHttpClient client) {
    return new ResourceClient(client, InterfaceUrls::feeFineActionsUrl, "feefineactions");
  }

  public static ResourceClient forLoanPolicies(OkapiHttpClient client) {
    return new ResourceClient(client, InterfaceUrls::loanPoliciesStorageUrl,
      "loan policies", "loanPolicies");
  }

  public static ResourceClient forRequestPolicies(OkapiHttpClient client) {
    return new ResourceClient(client, InterfaceUrls::requestPoliciesStorageUrl,
      "request policies", "requestPolicies");
  }

  public static ResourceClient forNoticePolicies(OkapiHttpClient client) {
    return new ResourceClient(client, InterfaceUrls::noticePoliciesStorageUrl,
      "notice policies", "noticePolicies");
  }

  public static ResourceClient forOverdueFinePolicies(OkapiHttpClient client) {
    return new ResourceClient(client, InterfaceUrls::overdueFinesPoliciesStorageUrl,
      "overdue fines policies", "overdueFinePolicies");
  }

  public static ResourceClient forFixedDueDateSchedules(OkapiHttpClient client) {
    return new ResourceClient(client, InterfaceUrls::fixedDueDateSchedulesStorageUrl,
      "fixed due date schedules", "fixedDueDateSchedules");
  }

  public static ResourceClient forCirculationRules(OkapiHttpClient client) {
    return new ResourceClient(client, InterfaceUrls::circulationRulesStorageUrl,
      "circulation rules", "circulationRules");
  }

  public static ResourceClient forUsers(OkapiHttpClient client) {
    return new ResourceClient(client, InterfaceUrls::usersUrl,
      "users");
  }

  public static ResourceClient forCalendar(OkapiHttpClient client) {
    return new ResourceClient(client, InterfaceUrls::calendarUrl,
      "calendar", "calendars");
  }

  public static ResourceClient forProxyRelationships(OkapiHttpClient client) {
    return new ResourceClient(client, InterfaceUrls::proxyRelationshipsUrl,
      "proxiesFor");
  }

  public static ResourceClient forPatronGroups(OkapiHttpClient client) {
    return new ResourceClient(client, InterfaceUrls::patronGroupsStorageUrl,
      "patron groups", "usergroups");
  }

  public static ResourceClient forAddressTypes(OkapiHttpClient client) {
    return new ResourceClient(client, InterfaceUrls::addressTypesUrl,
      "address types", "addressTypes");
  }

  public static ResourceClient forLoansStorage(OkapiHttpClient client) {
    return new ResourceClient(client, InterfaceUrls::loansStorageUrl,
      "storage loans", "loans");
  }

  public static ResourceClient forRequestsStorage(OkapiHttpClient client) {
    return new ResourceClient(client, InterfaceUrls::requestStorageUrl,
      "storage requests", "requests");
  }

  public static ResourceClient forMaterialTypes(OkapiHttpClient client) {
    return new ResourceClient(client, InterfaceUrls::materialTypesStorageUrl,
      "material types", "mtypes");
  }

  public static ResourceClient forLoanTypes(OkapiHttpClient client) {
    return new ResourceClient(client, InterfaceUrls::loanTypesStorageUrl,
      "loan types", "loantypes");
  }

  public static ResourceClient forInstanceTypes(OkapiHttpClient client) {
    return new ResourceClient(client, InterfaceUrls::instanceTypesStorageUrl,
      "instance types", "instanceTypes");
  }

  public static ResourceClient forContributorNameTypes(OkapiHttpClient client) {
    return new ResourceClient(client, InterfaceUrls::contributorNameTypesStorageUrl,
      "contributor name types", "contributorNameTypes");
  }

  public static ResourceClient forInstitutions(OkapiHttpClient client) {
    return new ResourceClient(client, InterfaceUrls::institutionsStorageUrl,
      "institutions", "locinsts");
  }

  public static ResourceClient forCampuses(OkapiHttpClient client) {
    return new ResourceClient(client, InterfaceUrls::campusesStorageUrl,
      "campuses", "loccamps");
  }

  public static ResourceClient forLibraries(OkapiHttpClient client) {
    return new ResourceClient(client, InterfaceUrls::librariesStorageUrl,
      "libraries", "loclibs");
  }

  public static ResourceClient forLocations(OkapiHttpClient client) {
    return new ResourceClient(client, InterfaceUrls::locationsStorageUrl,
      "locations");
  }

  public static ResourceClient forCancellationReasons(OkapiHttpClient client) {
    return new ResourceClient(client, InterfaceUrls::cancellationReasonsStorageUrl,
      "cancellationReasons");
  }

  public static ResourceClient forServicePoints(OkapiHttpClient client) {
    return new ResourceClient(client, InterfaceUrls::servicePointsStorageUrl,
      "service points", "servicepoints");
  }

  public static ResourceClient forPatronNotices(OkapiHttpClient client) {
    return new ResourceClient(client, InterfaceUrls::patronNoticesUrl,
      "patron notice", "patronnotices");
  }

  public static ResourceClient forScheduledNotices(OkapiHttpClient client) {
    return new ResourceClient(client, InterfaceUrls::scheduledNoticesUrl,
      "scheduled notice", "scheduledNotices");
  }

  public static ResourceClient forPatronSessionRecords(OkapiHttpClient client) {
    return new ResourceClient(client, InterfaceUrls::patronActionSessionsUrl,
      "patron session records", "patronActionSessions");
  }

  public static ResourceClient forExpiredSessions(OkapiHttpClient client) {
    return new ResourceClient(client, InterfaceUrls::patronExpiredSessionsUrl,
      "expired session records");
  }

  public static ResourceClient forConfiguration(OkapiHttpClient client) {
    return new ResourceClient(client, InterfaceUrls::configurationUrl,
      "configuration entries", "configs");
  }

  public static ResourceClient forRenewByBarcode(OkapiHttpClient client) {
    return new ResourceClient(client, subPath -> InterfaceUrls.renewByBarcodeUrl(),
      "renew by barcode");
  }

  public static ResourceClient forRenewById(OkapiHttpClient client) {
    return new ResourceClient(client, subPath -> InterfaceUrls.renewByIdUrl(),
      "renew by id");
  }

  public static ResourceClient forOverrideRenewalByBarcode(OkapiHttpClient client) {
    return new ResourceClient(
      client, subPath -> InterfaceUrls.overrideRenewalByBarcodeUrl(),
      "override renewal by barcode"
    );
  }

  private ResourceClient(
    OkapiHttpClient client,
    UrlMaker urlMaker,
    String resourceName,
    String collectionArrayPropertyName) {

    this.client = client;
    this.urlMaker = urlMaker;
    this.resourceName = resourceName;
    this.collectionArrayPropertyName = collectionArrayPropertyName;
  }

  private ResourceClient(
    OkapiHttpClient client,
    UrlMaker urlMaker,
    String resourceName) {

    this.client = client;
    this.urlMaker = urlMaker;
    this.resourceName = resourceName;
    this.collectionArrayPropertyName = resourceName;
  }

  public Response attemptCreate(Builder builder)
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    client.post(urlMaker.combine(""), builder.create(),
      ResponseHandler.any(createCompleted));

    return createCompleted.get(5, TimeUnit.SECONDS);
  }

  public IndividualResource create(Builder builder)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return create(builder.create());
  }

  public IndividualResource create(JsonObject request)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    log.debug("Attempting to create {} record: {}", resourceName,
      request.encodePrettily());

    client.post(urlMaker.combine(""), request,
      ResponseHandler.any(createCompleted));

    Response response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(
      String.format("Failed to create %s: %s", resourceName,
        response.getBody()), response.getStatusCode(), is(HTTP_CREATED));

    log.debug("Created resource {}: {}", resourceName,
      response.getJson().encodePrettily());

    return new IndividualResource(response);
  }

  public Response attemptCreateAtSpecificLocation(Builder builder)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    JsonObject representation = builder.create();
    String id = representation.getString("id");

    final URL location = urlMaker.combine(String.format("/%s", id));

    client.put(location, representation, ResponseHandler.any(createCompleted));

    return createCompleted.get(5, TimeUnit.SECONDS);
  }

  public IndividualResource createAtSpecificLocation(Builder builder)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    JsonObject representation = builder.create();
    String id = representation.getString("id");

    final URL location = urlMaker.combine(String.format("/%s", id));

    client.put(location, representation, ResponseHandler.any(createCompleted));

    Response createResponse = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(
      String.format("Failed to create %s %s: %s", resourceName, id, createResponse.getBody()),
      createResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    System.out.println(String.format("Created resource %s: %s", resourceName,
      createResponse.getJson().encodePrettily()));

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(location, ResponseHandler.any(getCompleted));

    Response getResponse = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(
      String.format("Failed to get %s %s: %s", resourceName, id, getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    return new IndividualResource(getResponse);
  }

  public Response attemptReplace(UUID id, Builder builder)
      throws MalformedURLException,
      InterruptedException,
      ExecutionException,
      TimeoutException {
    return attemptReplace(id, builder.create());
  }

  public Response attemptReplace(UUID id, JsonObject jsonObject)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    String path = "";
    if (id != null) {
      path = String.format("/%s", id);
    }

    client.put(urlMaker.combine(path), jsonObject,
      ResponseHandler.any(putCompleted));

    return putCompleted.get(5, TimeUnit.SECONDS);
  }

  public void replace(UUID id, Builder builder)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    replace(id, builder.create());
  }

  public void replace(UUID id, JsonObject request)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    String path = "";
    if (id != null) {
      path = String.format("/%s", id);
    }
    client.put(urlMaker.combine(path), request,
      ResponseHandler.any(putCompleted));

    Response putResponse = putCompleted.get(5, TimeUnit.SECONDS);

    assertThat(
      String.format("Failed to update %s %s: %s", resourceName, id, putResponse.getBody()),
      putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
  }

  public Response getById(UUID id)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(urlMaker.combine(String.format("/%s", id)),
      ResponseHandler.any(getCompleted));

    return getCompleted.get(5, TimeUnit.SECONDS);
  }

  public IndividualResource get(IndividualResource record)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    return get(record.getId());
  }


  public IndividualResource get(UUID id)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(urlMaker.combine(String.format("/%s", id)),
      ResponseHandler.any(getCompleted));

    final Response response = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(
      String.format("Failed to get %s: %s", resourceName, response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    System.out.println(String.format("Found resource %s: %s", resourceName,
      response.getJson().encodePrettily()));

    return new IndividualResource(response);
  }

  public Response attemptGet(IndividualResource resource)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(urlMaker.combine(String.format("/%s", resource.getId())),
      ResponseHandler.any(getCompleted));

    return getCompleted.get(5, TimeUnit.SECONDS);
  }

  public void delete(UUID id)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<Response> deleteFinished = new CompletableFuture<>();

    client.delete(urlMaker.combine(String.format("/%s", id)),
      ResponseHandler.any(deleteFinished));

    Response response = deleteFinished.get(5, TimeUnit.SECONDS);

    assertThat(String.format(
      "Failed to delete %s %s: %s", resourceName, id, response.getBody()),
      response.getStatusCode(), CoreMatchers.anyOf(
        is(HttpURLConnection.HTTP_NO_CONTENT),
        is(HttpURLConnection.HTTP_NOT_FOUND)));
  }

  public void delete(IndividualResource resource)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    delete(resource.getId());
  }

  public void deleteAll()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<Response> deleteAllFinished = new CompletableFuture<>();

    client.delete(urlMaker.combine(""),
      ResponseHandler.any(deleteAllFinished));

    Response response = deleteAllFinished.get(5, TimeUnit.SECONDS);

    assertThat(String.format(
      "Failed to delete %s: %s", resourceName, response.getBody()),
      response.getStatusCode(), is(204));
  }

  public void deleteAllIndividually()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    List<JsonObject> records = getAll();

    records.stream().forEach(record -> {
      try {
        CompletableFuture<Response> deleteFinished = new CompletableFuture<>();

        client.delete(urlMaker.combine(String.format("/%s",
          record.getString("id"))),
          ResponseHandler.any(deleteFinished));

        Response deleteResponse = deleteFinished.get(5, TimeUnit.SECONDS);

        assertThat(String.format(
          "Failed to delete %s: %s", resourceName, deleteResponse.getBody()),
          deleteResponse.getStatusCode(), is(204));

      } catch (Throwable e) {
        assertThat(String.format("Exception whilst deleting %s individually: %s",
          resourceName, e.toString()),
          true, is(false));
      }
    });
  }

  public List<JsonObject> getAll()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<Response> getFinished = new CompletableFuture<>();

    client.get(urlMaker.combine("?limit=1000"),
      ResponseHandler.any(getFinished));

    Response response = getFinished.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Get all records failed: %s", response.getBody()),
      response.getStatusCode(), is(200));

    JsonObject json = response.getJson();

    if(!json.containsKey(collectionArrayPropertyName)) {
      throw new RuntimeException(String.format(
        "Collection array property \"%s\" is not present in: %s",
        collectionArrayPropertyName, json.encodePrettily()));
    }

    return JsonArrayHelper.toList(json
      .getJsonArray(collectionArrayPropertyName));
  }

  public IndividualResource move(Builder builder)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return move(builder.create());
  }

  public Response attemptMove(Builder builder)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    CompletableFuture<Response> moveCompleted = new CompletableFuture<>();

    JsonObject request = builder.create();

    String path = String.format("/%s/move", request.getString("id"));

    client.post(urlMaker.combine(path), request,
      ResponseHandler.any(moveCompleted));

    return moveCompleted.get(15, TimeUnit.SECONDS);
  }

  public IndividualResource move(JsonObject request)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<Response> moveCompleted = new CompletableFuture<>();

    log.debug("Attempting to move {} record: {}", resourceName,
      request.encodePrettily());

    client.post(urlMaker.combine(String.format("/%s/move", request.getString("id"))), request,
      ResponseHandler.any(moveCompleted));

    Response response = moveCompleted.get(5, TimeUnit.SECONDS);

    assertThat(
      String.format("Failed to move %s: %s", resourceName,
        response.getBody()), response.getStatusCode(), is(HTTP_OK));

    log.debug("Moved resource {}: {}", resourceName,
      response.getJson().encodePrettily());

    return new IndividualResource(response);
  }


  @FunctionalInterface
  public interface UrlMaker {
    URL combine(String subPath) throws MalformedURLException;
  }
}
