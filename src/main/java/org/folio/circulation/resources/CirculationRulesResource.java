package org.folio.circulation.resources;

import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;
import static org.folio.circulation.support.http.server.ServerErrorResponse.internalError;

import java.lang.invoke.MethodHandles;

import org.folio.circulation.rules.CirculationRulesException;
import org.folio.circulation.rules.Text2Drools;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.JsonResponseResult;
import org.folio.circulation.support.OkJsonResponseResult;
import org.folio.circulation.support.http.server.ForwardResponse;
import org.folio.circulation.support.http.server.SuccessResponse;
import org.folio.circulation.support.http.server.WebContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

/**
 * Write and read the circulation rules.
 */
public class CirculationRulesResource extends Resource {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final String rootPath;

  /**
   * Set the URL path.
   * @param rootPath  URL path
   * @param client
   */
  public CirculationRulesResource(String rootPath, HttpClient client) {
    super(client);
    this.rootPath = rootPath;
  }

  /**
   * Register the path set in the constructor.
   * @param router  where to register
   */
  public void register(Router router) {
    router.put(rootPath).handler(BodyHandler.create());

    router.get(rootPath).handler(this::get);
    router.put(rootPath).handler(this::put);
  }

  private void get(RoutingContext routingContext) {
    final Clients clients = Clients.create(new WebContext(routingContext), client);
    CollectionResourceClient circulationRulesClient = clients.circulationRulesStorage();

    log.debug("get(RoutingContext) client={}", circulationRulesClient);

    if (circulationRulesClient == null) {
      internalError(routingContext.response(),
        "Cannot initialise client to storage interface");
      return;
    }

    circulationRulesClient.get()
      .thenAccept(result -> result
        .applySideEffect(response -> {
          try {
            if (response.getStatusCode() != 200) {
              ForwardResponse.forward(routingContext.response(), response);
              return;
            }
            JsonObject circulationRules = new JsonObject(response.getBody());

            new OkJsonResponseResult(circulationRules)
              .writeTo(routingContext.response());
          }
          catch (Exception e) {
            internalError(routingContext.response(), getStackTrace(e));
          }
    }, cause -> cause.writeTo(routingContext.response())));
  }

  //Cannot combine exception catching as cannot resolve overloaded method for error
  @SuppressWarnings("squid:S2147")
  private void put(RoutingContext routingContext) {
    final Clients clients = Clients.create(new WebContext(routingContext), client);
    CollectionResourceClient loansRulesClient = clients.circulationRulesStorage();

    if (loansRulesClient == null) {
      internalError(routingContext.response(),
        "Cannot initialise client to storage interface");
      return;
    }

    JsonObject rulesInput;
    try {
      // try to convert, do not safe if conversion fails
      rulesInput = routingContext.getBodyAsJson();
      Text2Drools.convert(rulesInput.getString("rulesAsText"));
    } catch (CirculationRulesException e) {
      circulationRulesError(routingContext.response(), e);
      return;
    } catch (DecodeException e) {
      circulationRulesError(routingContext.response(), e);
      return;
    } catch (Exception e) {
      internalError(routingContext.response(), getStackTrace(e));
      return;
    }
    LoanCirculationRulesEngineResource.clearCache(new WebContext(routingContext).getTenantId());
    RequestCirculationRulesEngineResource.clearCache(new WebContext(routingContext).getTenantId());

    loansRulesClient.put(rulesInput.copy()).thenAccept(result ->
      result.applySideEffect(response -> {
        if (response.getStatusCode() == 204) {
          SuccessResponse.noContent(routingContext.response());
        } else {
          ForwardResponse.forward(routingContext.response(), response);
        }
    }, cause -> cause.writeTo(routingContext.response())));
  }

  private static void circulationRulesError(HttpServerResponse response, CirculationRulesException e) {
    JsonObject body = new JsonObject();
    body.put("message", e.getMessage());
    body.put("line", e.getLine());
    body.put("column", e.getColumn());
    new JsonResponseResult(422, body, null).writeTo(response);
  }

  private static void circulationRulesError(HttpServerResponse response, DecodeException e) {
    JsonObject body = new JsonObject();
    body.put("message", e.getMessage());  // already contains line and column number
    new JsonResponseResult(422, body, null).writeTo(response);
  }
}
