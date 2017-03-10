package org.folio.circulation.api;

import org.folio.circulation.CirculationVerticle;
import org.folio.circulation.api.fakes.FakeOkapi;
import org.folio.circulation.api.support.URLHelper;
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
import java.util.function.Consumer;

@RunWith(Suite.class)

@Suite.SuiteClasses({
  LoanAPITests.class
})
public class APITestSuite {

  public static final String TENANT_ID = "test_tenant";

  private static VertxAssistant vertxAssistant;
  private static int port;
  private static String circulationModuleDeploymentId;
  private static String fakeOkapiDeploymentId;

  private static Boolean useOkapiForStorage;

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

  public static HttpClient createHttpClient(
    Consumer<Throwable> exceptionHandler) {

    return new HttpClient(vertxAssistant, okapiUrl(), exceptionHandler);
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
  }

  @AfterClass
  public static void after()
    throws InterruptedException, ExecutionException,
    TimeoutException, MalformedURLException {

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
}
