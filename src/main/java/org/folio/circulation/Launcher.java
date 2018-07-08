package org.folio.circulation;

import io.vertx.core.logging.Logger;
import org.folio.circulation.support.VertxAssistant;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.vertx.core.logging.LoggerFactory.getLogger;

public class Launcher {
  private String moduleDeploymentId;
  private final VertxAssistant vertxAssistant;

  private Launcher(VertxAssistant vertxAssistant) {
    this.vertxAssistant = vertxAssistant;
  }

  public static void main(String[] args) throws
    InterruptedException,
    ExecutionException,
    TimeoutException {

    Logging.initialiseFormat();

    final Launcher launcher = new Launcher(new VertxAssistant());

    HashMap<String, Object> config = new HashMap<>();

    Integer port = Integer.getInteger("port", 9801);

    putNonNullConfig("port", port, config);

    Runtime.getRuntime().addShutdownHook(new Thread(launcher::stop));

    launcher.start(config);
  }

  private void stop() {
    CompletableFuture<Void> undeployed = new CompletableFuture<>();
    CompletableFuture<Void> stopped = new CompletableFuture<>();
    CompletableFuture<Void> all = CompletableFuture.allOf(undeployed, stopped);

    final Logger log = getLogger(MethodHandles.lookup().lookupClass());

    log.info("Server Stopping");

    vertxAssistant.undeployVerticle(moduleDeploymentId, undeployed);

    undeployed.thenAccept(result -> vertxAssistant.stop(stopped));

    all.join();
    log.info("Server Stopped");
  }

  public void start(Map<String, Object> config) throws
    InterruptedException,
    ExecutionException,
    TimeoutException {

    vertxAssistant.start();

    final Logger log = getLogger(MethodHandles.lookup().lookupClass());

    log.info("Server Starting");

    CompletableFuture<String> deployed = new CompletableFuture<>();

    vertxAssistant.deployVerticle(CirculationVerticle.class.getName(),
      config, deployed);

    deployed.thenAccept(result -> log.info("Server Started"));

    moduleDeploymentId = deployed.get(10, TimeUnit.SECONDS);
  }

  private static void putNonNullConfig(String key,
                                       Object value,
                                       Map<String, Object> config) {
    if(value != null) {
      config.put(key, value);
    }
  }
}
