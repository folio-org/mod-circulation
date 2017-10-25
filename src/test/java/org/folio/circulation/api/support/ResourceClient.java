package org.folio.circulation.api.support;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.JsonArrayHelper;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.OkapiHttpClient;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.hamcrest.MatcherAssert;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class ResourceClient {

  private final OkapiHttpClient client;
  private final UrlMaker urlMaker;
  private final String resourceName;

  public static ResourceClient forItems(OkapiHttpClient client) {
    return new ResourceClient(client, InterfaceUrls::itemsStorageUrl, "item");
  }

  public static ResourceClient forRequests(OkapiHttpClient client) {
    return new ResourceClient(client, InterfaceUrls::requestsUrl, "request");
  }

  public static ResourceClient forLoans(OkapiHttpClient client) {
    return new ResourceClient(client, InterfaceUrls::loansUrl, "loans");
  }

  public static ResourceClient forUsers(OkapiHttpClient client) {
    return new ResourceClient(client, InterfaceUrls::usersUrl, "users");
  }

  public static ResourceClient forLoansStorage(OkapiHttpClient client) {
    return new ResourceClient(client, InterfaceUrls::loansStorageUrl, "storage loans");
  }

  public static ResourceClient forMaterialTypes(OkapiHttpClient client) {
    return new ResourceClient(client, InterfaceUrls::materialTypesStorageUrl, "material types");
  }

  public static ResourceClient forLoanTypes(OkapiHttpClient client) {
    return new ResourceClient(client, InterfaceUrls::loanTypesStorageUrl, "loan types");
  }

  public static ResourceClient forLocations(OkapiHttpClient client) {
    return new ResourceClient(client, InterfaceUrls::locationsStorageUrl, "locations");
  }

  private ResourceClient(
    OkapiHttpClient client,
    UrlMaker urlMaker, String resourceName) {

    this.client = client;
    this.urlMaker = urlMaker;
    this.resourceName = resourceName;
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

    assertThat(String.format("Failed to create %s: %s", resourceName,
      response.getBody()), response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    return new IndividualResource(response);
  }

  public void replace(UUID id, JsonObject request)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    client.put(urlMaker.combine(String.format("/%s", id)), request,
      ResponseHandler.any(putCompleted));

    Response putResponse = putCompleted.get(5, TimeUnit.SECONDS);

    assertThat(
      String.format("Failed to update %s %s: %s", putResponse.getBody(), resourceName, id),
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

    Response response = getCompleted.get(5, TimeUnit.SECONDS);

    return response;
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

    assertThat(String.format("Failed to delete %s %s", resourceName, id),
      response.getStatusCode(), is(204));
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

    MatcherAssert.assertThat("WARNING!!!!! Delete all resources failed",
      response.getStatusCode(), is(204));
  }

  public void deleteAllIndividually(String collectionArrayName)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    List<JsonObject> records = getAll(collectionArrayName);

    records.stream().forEach(record -> {
      try {
        CompletableFuture<Response> deleteFinished = new CompletableFuture<>();

        client.delete(urlMaker.combine(String.format("/%s",
          record.getString("id"))),
          ResponseHandler.any(deleteFinished));

        Response deleteResponse = deleteFinished.get(5, TimeUnit.SECONDS);

        MatcherAssert.assertThat("WARNING!!!!! Delete a resource individually failed",
          deleteResponse.getStatusCode(), is(204));
      } catch (Throwable e) {
        assertThat("WARNING!!!!! Delete a resource individually failed",
          true, is(false));
      }
    });
  }

  public List<JsonObject> getAll(String collectionArrayName)
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

    return JsonArrayHelper.toList(response.getJson()
      .getJsonArray(collectionArrayName));
  }

  @FunctionalInterface
  public interface UrlMaker {
    URL combine(String subPath) throws MalformedURLException;
  }
}
