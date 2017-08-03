package org.folio.circulation;

import org.folio.circulation.support.VertxAssistant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Launcher {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static String moduleDeploymentId;
  private static VertxAssistant vertxAssistant = new VertxAssistant();

  public static void main(String[] args) throws
    InterruptedException,
    ExecutionException,
    TimeoutException {

    Runtime.getRuntime().addShutdownHook(new Thread(() -> stop()));

    HashMap<String, Object> config = new HashMap<>();

    Integer port = Integer.getInteger("port", 9801);

    putNonNullConfig("port", port, config);

    start(config);
  }

  public static void start(Map<String, Object> config) throws
    InterruptedException,
    ExecutionException,
    TimeoutException {

    vertxAssistant.start();

    log.info("Server Starting");

    CompletableFuture<String> deployed = new CompletableFuture<>();

    vertxAssistant.deployVerticle(CirculationVerticle.class.getName(),
      config, deployed);

    deployed.thenAccept(result -> log.info("Server Started"));

    moduleDeploymentId = deployed.get(10, TimeUnit.SECONDS);
  }

  public static void stop() {
    CompletableFuture<Void> undeployed = new CompletableFuture<>();
    CompletableFuture<Void> stopped = new CompletableFuture<>();
    CompletableFuture<Void> all = CompletableFuture.allOf(undeployed, stopped);

    log.info("Server Stopping");

    vertxAssistant.undeployVerticle(moduleDeploymentId, undeployed);

    undeployed.thenAccept(result -> vertxAssistant.stop(stopped));

    all.join();
    log.info("Server Stopped");
  }

  private static void putNonNullConfig(String key,
                                       Object value,
                                       Map<String, Object> config) {
    if(value != null) {
      config.put(key, value);
    }
  }
}
