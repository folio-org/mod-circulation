package org.folio.circulation.api;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.CirculationVerticle;
import org.folio.circulation.api.fakes.FakeOkapi;
import org.folio.circulation.api.support.URLHelper;
import org.folio.circulation.support.JsonArrayHelper;
import org.folio.circulation.support.VertxAssistant;
import org.folio.circulation.support.http.client.OkapiHttpClient;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

@RunWith(Suite.class)

@Suite.SuiteClasses({
  LoanAPITests.class
})
public class APITestSuite {

  public static final String TENANT_ID = "test_tenant";
  public static final String TOKEN = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJhZG1pbiIsInRlbmFudCI6ImRlbW9fdGVuYW50In0.63jTgc15Kil946OdOGYZur_8xVWEUURANx87FAOQajh9TJbsnCMbjE164JQqNLMWShCyi9FOX0Kr1RFuiHTFAQ";

  private static VertxAssistant vertxAssistant;
  private static int port;
  private static String circulationModuleDeploymentId;
  private static String fakeOkapiDeploymentId;
  private static Boolean useOkapiForStorage;
  private static String bookMaterialTypeId;

  public static URL circulationModuleUrl(String path) {
    try {
      return new URL("http", "localhost", port, path);
    }
    catch(MalformedURLException ex) {
      return null;
    }
  }

  public static URL viaOkapiModuleUrl(String path) {
    try {
      return URLHelper.joinPath(okapiUrl(), path);
    }
    catch(MalformedURLException ex) {
      return null;
    }
  }

  public static OkapiHttpClient createClient(
    Consumer<Throwable> exceptionHandler) {

    return new OkapiHttpClient(
      vertxAssistant.createUsingVertx(vertx -> vertx.createHttpClient()),
      okapiUrl(), TENANT_ID, exceptionHandler);
  }

  public static void deleteAll(URL collectionResourceUrl)
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    OkapiHttpClient client = createClient(exception -> {
      System.out.println(
        String.format("Request to delete all failed: %s",
          exception.toString()));
    });

    CompletableFuture<Response> deleteAllFinished = new CompletableFuture<>();

    client.delete(collectionResourceUrl,
      ResponseHandler.any(deleteAllFinished));

    Response response = deleteAllFinished.get(5, TimeUnit.SECONDS);

    assertThat("WARNING!!!!! Delete all resources preparation failed",
      response.getStatusCode(), is(204));
  }

  @BeforeClass
  public static void before()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    useOkapiForStorage = Boolean.parseBoolean(
      System.getProperty("okapi.useForStorage", "false"));

    vertxAssistant = new VertxAssistant();

    port = 9605;

    HashMap<String, Object> config = new HashMap<>();

    config.put("port", port);

    vertxAssistant.start();

    CompletableFuture<String> fakeStorageModuleDeployed = new CompletableFuture<>();

    if(!useOkapiForStorage) {
        vertxAssistant.deployVerticle(FakeOkapi.class.getName(),
        new HashMap<>(), fakeStorageModuleDeployed);
    }
    else {
      fakeStorageModuleDeployed.complete(null);
    }

    CompletableFuture<String> circulationModuleDeployed =
      vertxAssistant.deployVerticle(CirculationVerticle.class.getName(),
        config);

    fakeOkapiDeploymentId = fakeStorageModuleDeployed.get(10, TimeUnit.SECONDS);
    circulationModuleDeploymentId = circulationModuleDeployed.get(10, TimeUnit.SECONDS);

    createMaterialTypes();
  }

  @AfterClass
  public static void after()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    deleteAll(viaOkapiModuleUrl("/item-storage/items"));
    deleteMaterialTypes();

    CompletableFuture<Void> circulationModuleUndeployed =
      vertxAssistant.undeployVerticle(circulationModuleDeploymentId);

    CompletableFuture<Void> fakeOkapiUndeployed = new CompletableFuture<>();

    if(!useOkapiForStorage) {
      vertxAssistant.undeployVerticle(fakeOkapiDeploymentId,
        fakeOkapiUndeployed);
    }
    else {
      fakeOkapiUndeployed.complete(null);
    }

    circulationModuleUndeployed.get(10, TimeUnit.SECONDS);
    fakeOkapiUndeployed.get(10, TimeUnit.SECONDS);

    CompletableFuture<Void> stopped = new CompletableFuture<>();

    vertxAssistant.stop(stopped);

    stopped.get(5, TimeUnit.SECONDS);
  }

  private static URL okapiUrl() {
    try {
      if(useOkapiForStorage) {
        return new URL("http://localhost:9130");
      }
      else {
        return new URL(FakeOkapi.getAddress());
      }
    }
    catch(MalformedURLException ex) {
      return null;
    }
  }

  private static void createMaterialTypes()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    OkapiHttpClient client = APITestSuite.createClient(exception -> {
      System.out.println(
        String.format("Request to material type storage module failed: %s",
          exception.toString()));
    });

    URL materialTypesUrl = new URL(okapiUrl() + "/material-type");

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(materialTypesUrl, ResponseHandler.any(getCompleted));

    Response getResponse = getCompleted.get(5 , TimeUnit.SECONDS);

    assertThat("Material Type API Unavaiable",
      getResponse.getStatusCode(), is(200));

    List<JsonObject> existingMaterialTypes = JsonArrayHelper.toList(
      getResponse.getJson().getJsonArray("mtypes"));

    if(existingMaterialTypes.stream()
      .noneMatch(materialType -> materialType.getString("name") == "Book")) {

      CompletableFuture<Response> createCompleted = new CompletableFuture<>();

      JsonObject bookMaterialType = new JsonObject().put("name", "Book");

      client.post(materialTypesUrl, bookMaterialType,
        ResponseHandler.json(createCompleted));

      Response creationResponse = createCompleted.get(5, TimeUnit.SECONDS);

      assertThat("Creation of Book material type resource failed",
        creationResponse.getStatusCode(), is(201));

      bookMaterialTypeId = creationResponse.getJson().getString("id");
    }
  }

  private static void deleteMaterialTypes()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    OkapiHttpClient client = APITestSuite.createClient(exception -> {
      System.out.println(
        String.format("Request to material type storage module failed: %s",
          exception.toString()));
    });

    String materialTypeUrl = okapiUrl()
      + String.format("/material-type/%s", bookMaterialTypeId);

    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();

    client.delete(materialTypeUrl, ResponseHandler.any(deleteCompleted));

    Response deletionResponse = deleteCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format(
        "WARNING!!!!! Deletion of Book material type resource failed: %s\n %s",
        bookMaterialTypeId, deletionResponse.getBody()),
      deletionResponse.getStatusCode(), is(204));
  }
}
