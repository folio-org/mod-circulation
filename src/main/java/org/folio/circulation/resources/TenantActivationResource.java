package org.folio.circulation.resources;

import static org.folio.circulation.support.http.server.JsonHttpResponse.created;
import static org.folio.circulation.support.http.server.NoContentResponse.noContent;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.rules.cache.CirculationRulesCache;
import org.folio.circulation.services.PubSubRegistrationService;
import org.folio.circulation.services.events.KafkaService;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.ServerErrorResponse;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RequestBody;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class TenantActivationResource extends Resource {

  // For testing purposes, remove once mod-pubsub deprecation in complete
  private static boolean ENABLE_NATIVE_KAFKA_INTEGRATION = false;
  public static void enableNativeKafkaIntegration() {
    ENABLE_NATIVE_KAFKA_INTEGRATION = true;
  }
  public static void disableNativeKafkaIntegration() {
    ENABLE_NATIVE_KAFKA_INTEGRATION = false;
  }

  public TenantActivationResource(HttpClient client) {
    super(client);
  }

  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration("/_/tenant", router);
    routeRegistration.create(this::enableModuleForTenant);
    routeRegistration.deleteAll(this::disableModuleForTenant);
  }

  private void enableModuleForTenant(RoutingContext routingContext) {
    WebContext webContext = new WebContext(routingContext);
    Clients clients = Clients.create(webContext, client);

    createKafkaTopics(webContext, routingContext.vertx())
      .thenCompose(ignored -> warmUpCirculationRulesCache(webContext, clients))
      .thenRun(() -> created(new JsonObject()).writeTo(routingContext.response()))
      .exceptionally(throwable -> {
        ServerErrorResponse.internalError(routingContext.response(), throwable.getLocalizedMessage());
        return null;
      });
  }

  private CompletableFuture<Void> createKafkaTopics(WebContext webContext, Vertx  vertx) {
    String tenantId = webContext.getTenantId();
    Map<String, String> headers = webContext.getHeaders();

    return ENABLE_NATIVE_KAFKA_INTEGRATION
      ? new KafkaService(vertx).createCirculationTopics(tenantId)
      : PubSubRegistrationService.registerModule(headers, vertx);
  }

  private void disableModuleForTenant(RoutingContext routingContext) {
    deleteKafkaTopics(routingContext)
      .thenRun(() -> noContent().writeTo(routingContext.response()))
      .exceptionally(throwable -> {
        ServerErrorResponse.internalError(routingContext.response(), throwable.getLocalizedMessage());
        return null;
      });
  }

  private CompletableFuture<Void> deleteKafkaTopics(RoutingContext routingContext) {
    WebContext webContext = new WebContext(routingContext);

    return ENABLE_NATIVE_KAFKA_INTEGRATION && isPurgeRequested(routingContext)
      ? new KafkaService(routingContext.vertx()).deleteCirculationTopics(webContext.getTenantId())
      : CompletableFuture.completedFuture(null);
  }

  private boolean isPurgeRequested(RoutingContext context) {
    Boolean isPurgeRequested = Optional.ofNullable(context.body())
      .map(RequestBody::asJsonObject)
      .map(body -> body.getBoolean("purge"))
      .orElse(false);

    log.info("isPurgeRequested:: purge requested: {}", isPurgeRequested);
    return isPurgeRequested;
  }

  private CompletableFuture<Void> warmUpCirculationRulesCache(WebContext context, Clients clients) {
    log.info("warmUpCirculationRulesCache:: warming up circulation rules cache");

    return CirculationRulesCache.getInstance()
      .reloadRules(context.getTenantId(), clients.circulationRulesStorage())
      .thenAccept(r -> r.applySideEffect(
        rules -> log.info("warmUpCirculationRulesCache:: warm-up complete"),
        failure -> log.error("warmUpCirculationRulesCache:: warm-up failed: {}", failure)
      ));
  }
}
