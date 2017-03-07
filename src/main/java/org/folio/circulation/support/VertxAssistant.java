package org.folio.circulation.support;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

public class VertxAssistant {

  private Vertx vertx;

  public void useVertx(Consumer<Vertx> action) {
    action.accept(vertx);
  }

  public <T> T createUsingVertx(Function<Vertx, T> function) {
    return function.apply(vertx);
  }

  public void start() {
    if(this.vertx == null) {
      this.vertx = Vertx.vertx();
    }
  }

  public void stop(CompletableFuture<Void> stopped) {

    if (vertx != null) {
      vertx.close(result -> {
        if (result.succeeded()) {
          stopped.complete(null);
        } else {
          stopped.completeExceptionally(result.cause());
        }
      });

      stopped.thenAccept(result -> { this.vertx = null; });
    }
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

        System.out.println(String.format("%s deployed in %s milliseconds",
          verticleClass, elapsedTime));

        deployed.complete(result.result());
      } else {
        deployed.completeExceptionally(result.cause());
      }
    });
  }

  public void undeployVerticle(String deploymentId,
                               CompletableFuture<Void> undeployed) {

    vertx.undeploy(deploymentId, result -> {
      if (result.succeeded()) {
        undeployed.complete(null);
      } else {
        undeployed.completeExceptionally(result.cause());
      }
    });
  }
}
