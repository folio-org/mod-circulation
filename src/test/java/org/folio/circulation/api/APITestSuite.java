package org.folio.circulation.api;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.CirculationVerticle;
import org.folio.circulation.api.fakes.FakeOkapi;
import org.folio.circulation.api.requests.*;
import org.folio.circulation.api.support.ResourceClient;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import static org.folio.circulation.api.support.InterfaceUrls.loanTypesStorageUrl;
import static org.folio.circulation.api.support.InterfaceUrls.materialTypesStorageUrl;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

@RunWith(Suite.class)

@Suite.SuiteClasses({
  LoanAPITests.class,
  LoanRulesAPITests.class,
  LoanRulesEngineAPITests.class,
  RequestsAPICreationTests.class,
  RequestsAPIDeletionTests.class,
  RequestsAPIRetrievalTests.class,
  RequestsAPIUpdatingTests.class,
  RequestsAPIStatusChangeTests.class
})
public class APITestSuite {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public static final String TENANT_ID = "test_tenant";
  public static final String TOKEN = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJhZG1pbiIsInRlbmFudCI6ImRlbW9fdGVuYW50In0.63jTgc15Kil946OdOGYZur_8xVWEUURANx87FAOQajh9TJbsnCMbjE164JQqNLMWShCyi9FOX0Kr1RFuiHTFAQ";

  private static VertxAssistant vertxAssistant;
  private static int port;
  private static String circulationModuleDeploymentId;
  private static String fakeOkapiDeploymentId;
  private static Boolean useOkapiForStorage;
  private static Boolean useOkapiForInitialRequests;
  private static String bookMaterialTypeId;
  private static String canCirculateLoanTypeId;

  public static URL circulationModuleUrl(String path) {
    try {
      if(useOkapiForInitialRequests) {
        return URLHelper.joinPath(okapiUrl(), path);
      }

      else {
        return new URL("http", "localhost", port, path);
      }
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
      okapiUrl(), TENANT_ID, TOKEN, exceptionHandler);
  }

  public static String bookMaterialTypeId() {
    return bookMaterialTypeId;
  }

  public static String canCirculateLoanTypeId() {
    return canCirculateLoanTypeId;
  }

  @BeforeClass
  public static void before()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    useOkapiForStorage = Boolean.parseBoolean(
      System.getProperty("use.okapi.storage.requests", "false"));

    useOkapiForInitialRequests = Boolean.parseBoolean(
      System.getProperty("use.okapi.initial.requests", "false"));

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
    createLoanTypes();
  }

  @AfterClass
  public static void after()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    OkapiHttpClient client = APITestSuite.createClient(exception -> {
      log.error("Requests to delete all for clean up failed:", exception);
    });

    ResourceClient.forRequests(client).deleteAll();
    ResourceClient.forLoans(client).deleteAll();
    ResourceClient.forItems(client).deleteAll();
    ResourceClient.forUsers(client).deleteAllIndividually("users");

    deleteMaterialTypes();
    deleteLoanTypes();

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
      log.error("Request to material type storage module failed:", exception);
    });

    URL materialTypesUrl = materialTypesStorageUrl();

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(materialTypesUrl, ResponseHandler.any(getCompleted));

    Response getResponse = getCompleted.get(5 , TimeUnit.SECONDS);

    assertThat("Material Type API Unavailable",
      getResponse.getStatusCode(), is(200));

    List<JsonObject> existingMaterialTypes = JsonArrayHelper.toList(
      getResponse.getJson().getJsonArray("mtypes"));

    if(existingMaterialTypes.stream()
      .noneMatch(materialType -> materialType.getString("name").equals("Book"))) {

      CompletableFuture<Response> createCompleted = new CompletableFuture<>();

      JsonObject bookMaterialType = new JsonObject().put("name", "Book");

      client.post(materialTypesUrl, bookMaterialType,
        ResponseHandler.json(createCompleted));

      Response creationResponse = createCompleted.get(5, TimeUnit.SECONDS);

      assertThat("Creation of Book material type resource failed",
        creationResponse.getStatusCode(), is(201));

      bookMaterialTypeId = creationResponse.getJson().getString("id");
    }
    else {
      bookMaterialTypeId = existingMaterialTypes.stream()
        .filter(loanType -> loanType.getString("name").equals("Book"))
        .findFirst()
        .get()
        .getString("id");
    }
  }

  private static void deleteMaterialTypes()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    OkapiHttpClient client = APITestSuite.createClient(exception -> {
      log.error("Request to material type storage module failed:", exception);
    });

    URL materialTypeUrl = materialTypesStorageUrl(
      String.format("/%s", bookMaterialTypeId));

    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();

    client.delete(materialTypeUrl, ResponseHandler.any(deleteCompleted));

    Response deletionResponse = deleteCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format(
        "WARNING!!!!! Deletion of Book material type resource failed: %s\n %s",
        bookMaterialTypeId, deletionResponse.getBody()),
      deletionResponse.getStatusCode(), is(204));
  }

  private static void createLoanTypes()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    OkapiHttpClient client = APITestSuite.createClient(exception -> {
      log.error("Request to loan type storage module failed:", exception);
    });

    URL loanTypesUrl = loanTypesStorageUrl("");

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(loanTypesUrl, ResponseHandler.any(getCompleted));

    Response getResponse = getCompleted.get(5 , TimeUnit.SECONDS);

    assertThat("Loan Type API Unavailable",
      getResponse.getStatusCode(), is(200));

    List<JsonObject> existingLoanTypes = JsonArrayHelper.toList(
      getResponse.getJson().getJsonArray("loantypes"));

    if(existingLoanTypes.stream()
      .noneMatch(loanType -> loanType.getString("name").equals("Can Circulate"))) {

      CompletableFuture<Response> createCompleted = new CompletableFuture<>();

      JsonObject canCirculateLoanType = new JsonObject().put("name", "Can Circulate");

      client.post(loanTypesUrl, canCirculateLoanType,
        ResponseHandler.json(createCompleted));

      Response creationResponse = createCompleted.get(5, TimeUnit.SECONDS);

      assertThat("Creation of can circulate loan type resource failed",
        creationResponse.getStatusCode(), is(201));

      canCirculateLoanTypeId = creationResponse.getJson().getString("id");
    }
    else {
      canCirculateLoanTypeId = existingLoanTypes.stream()
        .filter(loanType -> loanType.getString("name").equals("Can Circulate"))
        .findFirst()
        .get()
        .getString("id");
    }
  }

  private static void deleteLoanTypes()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    OkapiHttpClient client = APITestSuite.createClient(exception -> {
      log.error("Request to loan type storage module failed:", exception);
    });

    URL loanTypeUrl = loanTypesStorageUrl(
      String.format("/%s", canCirculateLoanTypeId));

    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();

    client.delete(loanTypeUrl, ResponseHandler.any(deleteCompleted));

    Response deletionResponse = deleteCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format(
      "WARNING!!!!! Deletion of can circulate loan type resource failed: %s\n %s",
      canCirculateLoanTypeId, deletionResponse.getBody()),
      deletionResponse.getStatusCode(), is(204));
  }
}
