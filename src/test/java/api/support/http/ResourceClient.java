package api.support.http;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

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

import api.support.builders.Builder;
import io.vertx.core.json.JsonObject;

public class ResourceClient {
  
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

  public static ResourceClient forLoans(OkapiHttpClient client) {
    return new ResourceClient(client, InterfaceUrls::loansUrl,
      "loans");
  }

  public static ResourceClient forLoanPolicies(OkapiHttpClient client) {
    return new ResourceClient(client, InterfaceUrls::loanPoliciesStorageUrl,
      "loan policies", "loanPolicies");
  }

  public static ResourceClient forFixedDueDateSchedules(OkapiHttpClient client) {
    return new ResourceClient(client, InterfaceUrls::fixedDueDateSchedulesStorageUrl,
      "fixed due date schedules", "fixedDueDateSchedules");
  }

  public static ResourceClient forLoanRules(OkapiHttpClient client) {
    return new ResourceClient(client, InterfaceUrls::loanRulesStorageUrl,
      "loan rules", "loanRules");
  }

  public static ResourceClient forUsers(OkapiHttpClient client) {
    return new ResourceClient(client, InterfaceUrls::usersUrl,
      "users");
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

    client.post(urlMaker.combine(""), request,
      ResponseHandler.json(createCompleted));

    Response response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(
      String.format("Failed to create %s: %s", resourceName, response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    System.out.println(String.format("Created resource %s: %s", resourceName,
      response.getJson().encodePrettily()));

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

    CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    String path = "";
    if (id != null) {
      path = String.format("/%s", id);
    }

    client.put(urlMaker.combine(path), builder.create(),
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

    client.get(urlMaker.combine(""),
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

  @FunctionalInterface
  public interface UrlMaker {
    URL combine(String subPath) throws MalformedURLException;
  }
}
