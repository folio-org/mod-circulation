package org.folio.circulation.api;

import io.vertx.core.Vertx;
import org.folio.circulation.CirculationVerticle;
import org.folio.circulation.api.fakes.FakeLoanStorageModule;
import org.folio.circulation.support.VertxAssistant;
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
import java.util.function.Function;

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

  public static URL storageUrl(String path) throws MalformedURLException {
    return new URL("http", "localhost", port, path);
  }

  @BeforeClass
  public static void before()
    throws Exception {

    vertxAssistant = new VertxAssistant();

    port = 9605;

    HashMap<String, Object> config = new HashMap<>();

    config.put("port", port);

    vertxAssistant.start();

    CompletableFuture<String> fakeStorageModuleDeployed =
      vertxAssistant.deployVerticle(FakeLoanStorageModule.class.getName(),
      new HashMap<>());

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

    CompletableFuture<Void> fakeStorageModuleUndeployed =
      vertxAssistant.undeployVerticle(fakeLoanStorageModuleDeploymentId);

    CompletableFuture.allOf(circulationModuleUndeployed,
      fakeStorageModuleUndeployed).get(10, TimeUnit.SECONDS);

    CompletableFuture<Void> stopped = new CompletableFuture<>();

    vertxAssistant.stop(stopped);

    stopped.get(5, TimeUnit.SECONDS);
  }

  public static void useVertx(Consumer<Vertx> action) {
    vertxAssistant.useVertx(action);
  }

  public static <T> T createUsingVertx(Function<Vertx, T> function) {
    return vertxAssistant.createUsingVertx(function);
  }
}
