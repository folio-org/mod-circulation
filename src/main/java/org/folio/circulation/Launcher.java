package org.folio.circulation;

import java.lang.invoke.MethodHandles;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.support.VertxAssistant;
import org.folio.circulation.support.logging.Logging;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;

public class Launcher {
  private final VertxAssistant vertxAssistant;
  private final Logger log;
  private final Set<String> deploymentIds;

  public Launcher(VertxAssistant vertxAssistant) {
    Logging.initialiseFormat();

    this.vertxAssistant = vertxAssistant;
    this.log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
    this.deploymentIds = new HashSet<>();
  }

  public static void main(String[] args) throws
    InterruptedException,
    ExecutionException,
    TimeoutException {

    final Launcher launcher = new Launcher(new VertxAssistant());

    Runtime.getRuntime().addShutdownHook(new Thread(launcher::stop));

    Integer port = Integer.valueOf(
        System.getProperty("http.port", System.getProperty("port", "9801")));

    launcher.start(port).get(10, TimeUnit.SECONDS);
  }

  private void stop() {
    log.info("Server Stopping");

    undeploy()
      .thenComposeAsync(v -> vertxAssistant.stop())
      .thenAccept(v -> log.info("Server Stopped"));
  }

  public CompletableFuture<Void> undeploy() {
    return undeployVerticles();
  }

  public CompletableFuture<Void> start(Integer port) {
    if(port == null) {
      throw new IllegalArgumentException("port should not be null");
    }

    vertxAssistant.start();

    log.info("start:: server starting");

    return deployVerticle(CirculationVerticle.class, new JsonObject().put("port", port))
      .thenAccept(result -> log.info("start:: server started"))
      .thenCompose(v -> deployVerticle(EventConsumerVerticle.class, EventConsumerVerticle.buildConfig()));
  }

  private CompletableFuture<Void> deployVerticle(Class<? extends AbstractVerticle> verticleClass,
    JsonObject config) {

    return vertxAssistant.deployVerticle(verticleClass, config)
      .thenAccept(deploymentIds::add)
      .thenAccept(r -> log.info("deployVerticle:: verticle deployed: {}", verticleClass.getSimpleName()))
      .exceptionally(t -> {
        log.error("deployVerticle:: deployment failed: {}", verticleClass.getSimpleName(), t);
        return null;
      });
  }

  private CompletableFuture<Void> undeployVerticles() {
    return CompletableFuture.allOf(
      deploymentIds.stream()
        .map(vertxAssistant::undeployVerticle)
        .toArray(CompletableFuture[]::new));
  }

}
