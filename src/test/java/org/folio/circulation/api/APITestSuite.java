package org.folio.circulation.api;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.CirculationVerticle;
import org.folio.circulation.api.fakes.FakeOkapi;
import org.folio.circulation.api.loans.LoanAPILocationTests;
import org.folio.circulation.api.loans.LoanAPITests;
import org.folio.circulation.api.loans.LoanAPITitleTests;
import org.folio.circulation.api.loans.LoanAPIPolicyTests;
import org.folio.circulation.api.requests.*;
import org.folio.circulation.api.support.http.ResourceClient;
import org.folio.circulation.api.support.http.URLHelper;
import org.folio.circulation.support.VertxAssistant;
import org.folio.circulation.support.http.client.OkapiHttpClient;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;


@RunWith(Suite.class)

@Suite.SuiteClasses({
  LoanAPITests.class,
  LoanAPILocationTests.class,
  LoanAPITitleTests.class,
  LoanAPIPolicyTests.class,
  LoanRulesAPITests.class,
  LoanRulesEngineAPITests.class,
  RequestsAPICreationTests.class,
  RequestsAPICreateMultipleRequestsTests.class,
  RequestsAPIDeletionTests.class,
  RequestsAPIRetrievalTests.class,
  RequestsAPIUpdatingTests.class,
  RequestsAPIStatusChangeTests.class,
  RequestsAPILoanCheckInTests.class,
  RequestsAPILoanRenewalTests.class,
  RequestsAPILoanHistoryTests.class,
  RequestsAPITitleTests.class,
})
public class APITestSuite {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public static final String TENANT_ID = "test_tenant";

  private static final String TOKEN = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJhZG1pbiIsInRlbmFudCI6ImRlbW9fdGVuYW50In0.63jTgc15Kil946OdOGYZur_8xVWEUURANx87FAOQajh9TJbsnCMbjE164JQqNLMWShCyi9FOX0Kr1RFuiHTFAQ";

  private static VertxAssistant vertxAssistant;
  private static int port;
  private static String circulationModuleDeploymentId;
  private static String fakeOkapiDeploymentId;
  private static Boolean useOkapiForStorage;
  private static Boolean useOkapiForInitialRequests;
  private static UUID bookMaterialTypeId;
  private static UUID canCirculateLoanTypeId;
  private static UUID mainLibraryLocationId;
  private static UUID annexLocationId;
  private static UUID booksInstanceTypeId;
  private static UUID regularGroupId = UUID.fromString("4809963d-78fc-4260-8a05-0b76254c07ba");
  private static UUID alternateGroupId = UUID.fromString("4809963d-78fc-4260-8a05-0b76254c07ba");
  private static boolean initialised;
  private static UUID userId1;
  private static UUID userId2;
  private static JsonObject userRecord1 = new JsonObject().put("username", "bfrederi")
          .put("id", "25ff4681-ddb2-45c2-b855-6290871dfaf9")
          .put("patronGroup", regularGroupId.toString());
  private static JsonObject userRecord2  = new JsonObject().put("username", "lko")
          .put("id", "93771903-3a91-4a05-bbf3-f1479c7f3b78")
          .put("patronGroup", alternateGroupId.toString());

  private static UUID canCirculateLoanPolicyId;

  public static int circulationModulePort() {
    return port;
  }

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
      vertxAssistant.createUsingVertx(Vertx::createHttpClient),
      okapiUrl(), TENANT_ID, TOKEN, exceptionHandler);
  }

  public static OkapiHttpClient createClient() {
    return APITestSuite.createClient(exception -> {
      log.error("Request failed:", exception);
    });
  }

  public static UUID bookMaterialTypeId() {
    return bookMaterialTypeId;
  }

  public static UUID canCirculateLoanTypeId() {
    return canCirculateLoanTypeId;
  }

  public static UUID mainLibraryLocationId() {
    return mainLibraryLocationId;
  }

  public static UUID annexLocationId() {
    return annexLocationId;
  }

  public static UUID booksInstanceTypeId() {
    return booksInstanceTypeId;
  }

  public static UUID userId() {
    return userId1;
  }

  public static JsonObject userRecord1() {
    return userRecord1;
  }

  public static JsonObject userRecord2() {
    return userRecord2;
  }

  public static UUID regularGroupId() {
    return regularGroupId;
  }

  public static UUID alternateGroupId() {
    return alternateGroupId;
  }

  public static UUID canCirculateLoanPolicyId() { return canCirculateLoanPolicyId; }

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
    createLocations();
    createInstanceTypes();
    createUsers();
    createLoanPolicies();

    initialised = true;
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
    ResourceClient.forHoldings(client).deleteAll();
    ResourceClient.forInstances(client).deleteAll();

    ResourceClient.forUsers(client).deleteAllIndividually();

    deleteMaterialTypes();
    deleteLoanTypes();
    deleteLocations();
    deleteInstanceTypes();
    deleteLoanPolicies();

//    deleteUsers();

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

  public static void createUsers()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {
    ResourceClient usersClient = ResourceClient.forUsers(createClient());
    userId1 = createUserRecord(usersClient, userRecord1);
    userId2 = createUserRecord(usersClient, userRecord2);
  }

  private static void deleteUsers()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    ResourceClient usersClient = ResourceClient.forUsers(createClient());
    usersClient.delete(userId1);
    usersClient.delete(userId2);
  }

  private static void createMaterialTypes()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    bookMaterialTypeId = createReferenceRecord(
      ResourceClient.forMaterialTypes(createClient()), "Book");
  }

  private static void deleteMaterialTypes()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    ResourceClient materialTypesClient = ResourceClient.forMaterialTypes(createClient());

    materialTypesClient.delete(bookMaterialTypeId);
  }

  private static void createLoanTypes()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    canCirculateLoanTypeId = createReferenceRecord(
      ResourceClient.forLoanTypes(createClient()), "Can Circulate");
  }

  private static void deleteLoanTypes()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    ResourceClient loanTypesClient = ResourceClient.forLoanTypes(createClient());

    loanTypesClient.delete(canCirculateLoanTypeId);
  }

  private static void createLocations()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    mainLibraryLocationId = createReferenceRecord(
      ResourceClient.forLocations(createClient()), "Main Library");

    annexLocationId = createReferenceRecord(
      ResourceClient.forLocations(createClient()), "Annex");
  }

  private static void deleteLocations()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    ResourceClient locationsClient = ResourceClient.forLocations(createClient());

    locationsClient.delete(mainLibraryLocationId);
    locationsClient.delete(annexLocationId);
  }

  private static void createInstanceTypes()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    booksInstanceTypeId = createReferenceRecord(
      ResourceClient.forInstanceTypes(createClient()), "Books");
  }

  private static void deleteInstanceTypes()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    ResourceClient instanceTypesClient = ResourceClient.forInstanceTypes(createClient());

    instanceTypesClient.delete(booksInstanceTypeId());
  }

  private static void createLoanPolicies()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    ResourceClient client = ResourceClient.forLoanPolicies(createClient());

    JsonObject canCirculateLoanPolicy = new JsonObject()
      .put("name", "Can Circulate")
      .put("description", "Can circulate item")
      .put("loanable", true)
      .put("renewable", true)
      .put("loansPolicy", new JsonObject()
        .put("profileId", "ROLLING")
        .put("closedLibraryDueDateManagementId", "KEEP_CURRENT_DATE"))
      .put("renewalsPolicy", new JsonObject()
        .put("renewFromId", "CURRENT_DUE_DATE")
        .put("differentPeriod", false));

    canCirculateLoanPolicyId = client.create(canCirculateLoanPolicy).getId();
  }

  private static void deleteLoanPolicies()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    ResourceClient client = ResourceClient.forLoanPolicies(createClient());

    client.delete(canCirculateLoanPolicyId());
  }

  private static UUID createReferenceRecord(
    ResourceClient client,
    String name)

    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    List<JsonObject> existingRecords = client.getAll();

    if(existsInList(existingRecords, name)) {

      JsonObject newReferenceRecord = new JsonObject().put("name", name);

      return client.create(newReferenceRecord).getId();
    }
    else {
      return findFirstByName(existingRecords, name);
    }
  }

  public static UUID createUserRecord(
    ResourceClient client,
    JsonObject record)

    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {
    List<JsonObject> existingRecords = client.getAll();
    /*
    for(JsonObject j : existingRecords) {
      if(j.getString("id").equals(record.getString("id"))) {
        return UUID.fromString(j.getString("id"));
      }
    }
    */
    return client.create(record).getId();
  }

  private static UUID findFirstByName(List<JsonObject> existingRecords, String name) {
    return UUID.fromString(existingRecords.stream()
      .filter(record -> record.getString("name").equals(name))
      .findFirst()
      .get()
      .getString("id"));
  }

  private static boolean existsInList(List<JsonObject> existingRecords, String name) {
    return existingRecords.stream()
      .noneMatch(materialType -> materialType.getString("name").equals(name));
  }

  public static boolean isNotInitialised() {
    return !initialised;
  }
}
