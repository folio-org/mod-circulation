package org.folio.circulation;

import org.folio.circulation.support.VertxAssistant;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Launcher {

  private static String moduleDeploymentId;
  private static VertxAssistant vertxAssistant = new VertxAssistant();

  public static void main(String[] args) throws
    InterruptedException,
    ExecutionException,
    TimeoutException {

    Runtime.getRuntime().addShutdownHook(new Thread(() -> stop()));

    start(new HashMap<>());
  }

  public static void start(Map<String, Object> config) throws
    InterruptedException,
    ExecutionException,
    TimeoutException {

    vertxAssistant.start();

    System.out.println("Server Starting");

    CompletableFuture<String> deployed = new CompletableFuture<>();

    vertxAssistant.deployVerticle(CirculationVerticle.class.getName(),
      config, deployed);

    deployed.thenAccept(result -> System.out.println("Server Started"));

    moduleDeploymentId = deployed.get(20, TimeUnit.SECONDS);
  }

  public static void stop() {
    CompletableFuture<Void> undeployed = new CompletableFuture<>();
    CompletableFuture<Void> stopped = new CompletableFuture<>();
    CompletableFuture<Void> all = CompletableFuture.allOf(undeployed, stopped);

    System.out.println("Server Stopping");

    vertxAssistant.undeployVerticle(moduleDeploymentId, undeployed);

    undeployed.thenAccept(result -> vertxAssistant.stop(stopped));

    all.join();
    System.out.println("Server Stopped");
  }
}
