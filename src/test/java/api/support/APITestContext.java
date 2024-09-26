package api.support;

import static org.apache.kafka.clients.producer.ProducerConfig.ACKS_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG;
import static org.folio.rest.tools.utils.NetworkUtils.nextFreePort;

import java.lang.invoke.MethodHandles;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.Launcher;
import org.folio.circulation.support.VertxAssistant;
import org.folio.circulation.support.http.client.OkapiHttpClient;
import org.folio.circulation.support.http.client.VertxWebClientOkapiHttpClient;
import org.folio.circulation.support.utils.ClockUtil;

import api.support.fakes.FakeOkapi;
import api.support.fakes.FakeStorageModule;
import api.support.http.OkapiHeaders;
import api.support.http.URLHelper;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.admin.KafkaAdminClient;
import io.vertx.kafka.client.producer.KafkaProducer;
import lombok.SneakyThrows;

public class APITestContext {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public static final String TENANT_ID = "test_tenant";
  public static String tempTenantId;
  private static String USER_ID = "79ff2a8b-d9c3-5b39-ad4a-0a84025ab085";

  private static final String TOKEN = "eyJhbGciOiJIUzUxMiJ9eyJzdWIiOiJhZG1pbiIsInVzZXJfaWQiOiI3OWZmMmE4Yi1kOWMzLTViMzktYWQ0YS0wYTg0MDI1YWIwODUiLCJ0ZW5hbnQiOiJ0ZXN0X3RlbmFudCJ9BShwfHcNClt5ZXJ8ImQTMQtAM1sQEnhsfWNmXGsYVDpuaDN3RVQ9";
  public static final ZonedDateTime END_OF_CURRENT_YEAR_DUE_DATE = ClockUtil.getZonedDateTime()
    .withMonth(12)
    .withDayOfMonth(31)
    .withHour(23)
    .withMinute(59)
    .withSecond(59)
    .truncatedTo(ChronoUnit.SECONDS);

  private static final String REQUEST_ID = createFakeRequestId();

  private static final VertxAssistant vertxAssistant = initVertxAssistant();
  private static Launcher launcher;
  private static final int PORT = nextFreePort();

  private static String fakeOkapiDeploymentId;
  private static Boolean useOkapiForStorage = false;
  private static Boolean useOkapiForInitialRequests;

  static String getToken() {
    return TOKEN;
  }

  public static String getTenantId() {
    return Optional.ofNullable(tempTenantId).orElse(TENANT_ID);
  }

  public static void setTempTenantId(String tenantId) {
    tempTenantId = tenantId;
  }

  public static void clearTempTenantId() {
    setTempTenantId(null);
  }

  public static String getUserId() {
    return USER_ID;
  }

  public static void defaultUserId(){
    USER_ID = "79ff2a8b-d9c3-5b39-ad4a-0a84025ab085";
  }

  public static void setUserId(String userId) {
    USER_ID = userId;
  }

  public static int circulationModulePort() {
    return PORT;
  }

  public static URL circulationModuleUrl(String path) {
    try {
      if (useOkapiForInitialRequests) {
        return URLHelper.joinPath(okapiUrl(), path);
      } else {
        return new URL("http", "localhost", PORT, path);
      }
    } catch (MalformedURLException ex) {
      return null;
    }
  }

  public static URL viaOkapiModuleUrl(String path) {
    try {
      return URLHelper.joinPath(okapiUrl(), path);
    } catch (MalformedURLException ex) {
      return null;
    }
  }

  public static OkapiHttpClient createWebClient() {
    return VertxWebClientOkapiHttpClient.createClientUsing(
      vertxAssistant.createUsingVertx(Vertx::createHttpClient), okapiUrl(),
      TENANT_ID, TOKEN, USER_ID, REQUEST_ID);
  }

  static void deployVerticles()
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    final CompletableFuture<String> fakeStorageModuleDeployed = deployFakeStorageModules();

    final CompletableFuture<Void> circulationModuleStarted = launcher.start(PORT);

    fakeStorageModuleDeployed.thenAccept(result -> fakeOkapiDeploymentId = result);

    CompletableFuture.allOf(circulationModuleStarted, fakeStorageModuleDeployed)
      .get(10, TimeUnit.SECONDS);
  }

  private static CompletableFuture<String> deployFakeStorageModules() {
    useOkapiForStorage = Boolean.parseBoolean(
      System.getProperty("use.okapi.storage.requests", "false"));

    useOkapiForInitialRequests = Boolean.parseBoolean(
      System.getProperty("use.okapi.initial.requests", "false"));

    launcher = new Launcher(vertxAssistant);

    final CompletableFuture<String> fakeStorageModuleDeployed;

    if (!useOkapiForStorage) {
      fakeStorageModuleDeployed = vertxAssistant.deployVerticle(FakeOkapi.class);
    } else {
      fakeStorageModuleDeployed = CompletableFuture.completedFuture(null);
    }
    return fakeStorageModuleDeployed;
  }

  static void undeployVerticles()
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<Void> circulationModuleUndeployed = launcher.undeploy();

    final CompletableFuture<Void> fakeOkapiUndeployed;

    if (!useOkapiForStorage) {
      outputCQLQueryRequestsPerformedAgainstFakes();

      fakeOkapiUndeployed = vertxAssistant.undeployVerticle(fakeOkapiDeploymentId);
    } else {
      fakeOkapiUndeployed = CompletableFuture.completedFuture(null);
    }

    circulationModuleUndeployed.get(10, TimeUnit.SECONDS);
    fakeOkapiUndeployed.get(10, TimeUnit.SECONDS);

    CompletableFuture<Void> stopped = vertxAssistant.stop();

    stopped.get(5, TimeUnit.SECONDS);
  }

  private static void outputCQLQueryRequestsPerformedAgainstFakes() {
    final String sortedRequests = FakeStorageModule.getQueries()
      .sorted()
      .collect(Collectors.joining("\n"));

    log.info("Queries performed: {}", sortedRequests);
  }

  static URL okapiUrl() {
    try {
      if (useOkapiForStorage) {
        return new URL("http://localhost:9130");
      } else {
        return new URL(FakeOkapi.getAddress());
      }
    } catch (MalformedURLException ex) {
      throw new IllegalArgumentException("Invalid Okapi URL configured for tests");
    }
  }

  public static OkapiHeaders getOkapiHeadersFromContext() {
    return new OkapiHeaders(okapiUrl(), getTenantId(), getToken(), getUserId());
  }

  private static String createFakeRequestId() {
    return String.format("%s/fake-context", new Random().nextInt(999999));
  }

  @SneakyThrows
  public static String deployVerticle(Class<? extends AbstractVerticle> verticle, JsonObject config) {
    return vertxAssistant.deployVerticle(verticle, config)
      .get(30, TimeUnit.SECONDS);
  }

  @SneakyThrows
  public static void undeployVerticle(String deploymentId) {
    vertxAssistant.undeployVerticle(deploymentId)
      .get(30, TimeUnit.SECONDS);
  }

  public static KafkaProducer<String, JsonObject> createKafkaProducer(String kafkaUrl) {
    Properties config = new Properties();
    config.put(BOOTSTRAP_SERVERS_CONFIG, kafkaUrl);
    config.put(ACKS_CONFIG, "1");

    return vertxAssistant.createUsingVertx(vertx ->
      KafkaProducer.create(vertx, config, String.class, JsonObject.class));
  }

  public static KafkaAdminClient createKafkaAdminClient(String kafkaUrl) {
    Properties config = new Properties();
    config.put(BOOTSTRAP_SERVERS_CONFIG, kafkaUrl);

    return vertxAssistant.createUsingVertx(vertx -> KafkaAdminClient.create(vertx, config));
  }

  private static VertxAssistant initVertxAssistant() {
    VertxAssistant assistant = new VertxAssistant();
    assistant.start();
    return assistant;
  }
}
