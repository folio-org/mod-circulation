package org.folio.circulation;

import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import org.folio.circulation.support.VertxAssistant;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.vertx.core.logging.LoggerFactory.getLogger;
import static org.folio.circulation.support.JsonPropertyWriter.write;

public class Launcher {
  private final VertxAssistant vertxAssistant;
  private final Logger log;
  private String moduleDeploymentId;

  private Launcher(VertxAssistant vertxAssistant) {
    Logging.initialiseFormat();

    this.vertxAssistant = vertxAssistant;
    this.log = getLogger(MethodHandles.lookup().lookupClass());
  }

  public static void main(String[] args) throws
    InterruptedException,
    ExecutionException,
    TimeoutException {

    final Launcher launcher = new Launcher(new VertxAssistant());

    Runtime.getRuntime().addShutdownHook(new Thread(launcher::stop));

    Integer port = Integer.getInteger("port", 9801);

    launcher.start(port);
  }

  private void stop() {
    log.info("Server Stopping");

    vertxAssistant.undeployVerticle(moduleDeploymentId)
      .thenComposeAsync(v -> vertxAssistant.stop())
      .thenAccept(v -> log.info("Server Stopped"));
  }

  public void start(Integer port) throws
    InterruptedException,
    ExecutionException,
    TimeoutException {

    if(port == null) {
      throw new IllegalArgumentException("port should not be null");
    }

    vertxAssistant.start();

    log.info("Server Starting");


    JsonObject config = new JsonObject();
    write(config, "port", port);

    CompletableFuture<String> deployed =
      vertxAssistant.deployVerticle(CirculationVerticle.class.getName(), config);

    deployed.thenAccept(result -> log.info("Server Started"));

    moduleDeploymentId = deployed.get(10, TimeUnit.SECONDS);
  }
}
