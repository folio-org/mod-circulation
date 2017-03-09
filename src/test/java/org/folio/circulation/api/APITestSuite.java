package org.folio.circulation.api;

import org.folio.circulation.CirculationVerticle;
import org.folio.circulation.api.fakes.FakeLoanStorageModule;
import org.folio.circulation.support.VertxAssistant;
import org.folio.circulation.support.http.client.HttpClient;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RunWith(Suite.class)

@Suite.SuiteClasses({
  LoanAPITests.class
})
public class APITestSuite {

  public static final String TENANT_ID = "test_tenant";

  private static VertxAssistant vertxAssistant;
  private static int port;
  private static String circulationModuleDeploymentId;
  private static String fakeLoanStorageModuleDeploymentId;

  private static Boolean useOkapiForStorage;

  public static URL moduleUrl(String path) {
    try {
      return new URL("http", "localhost", port, path);
    }
    catch(MalformedURLException ex) {
      return null;
    }
  }

  @BeforeClass
  public static void before()
    throws Exception {

    useOkapiForStorage = Boolean.parseBoolean(
      System.getProperty("okapi.useForStorage", "false"));

    vertxAssistant = new VertxAssistant();

    port = 9605;

    HashMap<String, Object> config = new HashMap<>();

    config.put("port", port);

    vertxAssistant.start();

    CompletableFuture<String> fakeStorageModuleDeployed = new CompletableFuture<>();

    if(!useOkapiForStorage) {
        vertxAssistant.deployVerticle(FakeLoanStorageModule.class.getName(),
        new HashMap<>(), fakeStorageModuleDeployed);
    }
    else {
      fakeStorageModuleDeployed.complete(null);
    }

    CompletableFuture<String> circulationModuleDeployed =
      vertxAssistant.deployVerticle(CirculationVerticle.class.getName(),
        config);

    fakeLoanStorageModuleDeploymentId = fakeStorageModuleDeployed.get(10, TimeUnit.SECONDS);
    circulationModuleDeploymentId = circulationModuleDeployed.get(10, TimeUnit.SECONDS);
  }

  @AfterClass
  public static void after()
    throws InterruptedException, ExecutionException,
    TimeoutException, MalformedURLException {

    CompletableFuture<Void> circulationModuleUndeployed =
      vertxAssistant.undeployVerticle(circulationModuleDeploymentId);

    CompletableFuture<Void> fakeStorageModuleUndeployed = new CompletableFuture<>();

    if(!useOkapiForStorage) {
      vertxAssistant.undeployVerticle(fakeLoanStorageModuleDeploymentId,
        fakeStorageModuleUndeployed);
    }
    else {
      fakeStorageModuleUndeployed.complete(null);
    }

    circulationModuleUndeployed.get(10, TimeUnit.SECONDS);
    fakeStorageModuleUndeployed.get(10, TimeUnit.SECONDS);

    CompletableFuture<Void> stopped = new CompletableFuture<>();

    vertxAssistant.stop(stopped);

    stopped.get(5, TimeUnit.SECONDS);
  }

  public static HttpClient createHttpClient() {
    return new HttpClient(vertxAssistant, storageUrl(), exception -> {
      System.out.println(
        String.format("Request to circulation module failed: %s",
          exception.toString()));
    });
  }

  private static URL storageUrl() {
    try {
      if(useOkapiForStorage) {
        return new URL("http://localhost:9130/loan-storage/loans");
      }
      else {
        return new URL(FakeLoanStorageModule.getAddress());
      }
    }
    catch(MalformedURLException ex) {
      return null;
    }
  }
}
