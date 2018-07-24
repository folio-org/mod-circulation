package org.folio.circulation.support;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class VertxAssistant {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private Vertx vertx;

  public <T> T createUsingVertx(Function<Vertx, T> function) {
    return function.apply(vertx);
  }

  public void start() {
    if (vertx != null) {
      return;
    }

    vertx = Vertx.vertx();
    vertx.exceptionHandler(ex -> log.error("Unhandled exception caught by vertx", ex));
  }

  public CompletableFuture<Void> stop() {
    final CompletableFuture<Void> future = new CompletableFuture<>();

    if (vertx != null) {
      vertx.close(result -> {
        if (result.succeeded()) {
          future.complete(null);
        } else {
          future.completeExceptionally(result.cause());
        }
      });

      future.thenAccept(result -> this.vertx = null);
    }

    return future;
  }

  public void deployVerticle(String verticleClass,
                            Map<String, Object> config,
                            CompletableFuture<String> deployed) {

    long startTime = System.currentTimeMillis();

    DeploymentOptions options = new DeploymentOptions();

    options.setConfig(new JsonObject(config));
    options.setWorker(true);

    vertx.deployVerticle(verticleClass, options, result -> {
      if (result.succeeded()) {
        long elapsedTime = System.currentTimeMillis() - startTime;

        log.info("{} deployed in {} milliseconds", verticleClass, elapsedTime);

        deployed.complete(result.result());
      } else {
        deployed.completeExceptionally(result.cause());
      }
    });
  }

  public CompletableFuture<String> deployVerticle(String verticleClass,
                             Map<String, Object> config) {

    CompletableFuture<String> deployed = new CompletableFuture<>();

    deployVerticle(verticleClass, config, deployed);

    return deployed;
  }

  public CompletableFuture<Void> undeployVerticle(String moduleDeploymentId) {
    CompletableFuture<Void> undeployed = new CompletableFuture<>();

    vertx.undeploy(moduleDeploymentId, result -> {
      if (result.succeeded()) {
        undeployed.complete(null);
      } else {
        undeployed.completeExceptionally(result.cause());
      }
    });

    return undeployed;
  }
}
