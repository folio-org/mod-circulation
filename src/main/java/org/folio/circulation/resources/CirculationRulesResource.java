package org.folio.circulation.resources;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;
import static org.folio.circulation.support.http.server.JsonHttpResponse.ok;
import static org.folio.circulation.support.http.server.JsonHttpResponse.unprocessableEntity;
import static org.folio.circulation.support.http.server.NoContentResponse.noContent;
import static org.folio.circulation.support.http.server.ServerErrorResponse.internalError;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.results.Result.of;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.antlr.v4.runtime.Token;
import org.apache.commons.collections4.MapUtils;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.rules.CirculationRulesException;
import org.folio.circulation.rules.CirculationRulesParser;
import org.folio.circulation.rules.Text2Drools;
import org.folio.circulation.rules.cache.CirculationRulesCache;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.ForwardOnFailure;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.server.ForwardResponse;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.Result;
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
  private static final int FIRST_ELEMENT_OF_LIST = 0;
  private static final int POLICY_ID_POSITION_NUMBER = 1;
  private static final PageLimit POLICY_PAGE_LIMIT = PageLimit.limit(1000);
  private final String rootPath;

  /**
   * Set the URL path.
   * @param rootPath  URL path
   * @param client HTTP client
   */
  public CirculationRulesResource(String rootPath, HttpClient client) {
    super(client);
    this.rootPath = rootPath;
  }

  /**
   * Register the path set in the constructor.
   * @param router  where to register
   */
  @Override
  public void register(Router router) {
    router.put(rootPath).handler(BodyHandler.create());
    router.get(rootPath).handler(this::get);
    router.put(rootPath).handler(this::put);
  }

  private void get(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);
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

            context.write(ok(circulationRules));
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

    getPolicyIdsByType(clients)
      .thenAccept(result -> proceedWithUpdate(result.value(), routingContext, clients));
  }

  private void proceedWithUpdate(Map<String, Set<String>> existingPoliciesIds,
    RoutingContext routingContext, Clients clients) {

    final WebContext webContext = new WebContext(routingContext);

    JsonObject rulesInput;
    try {
      // try to convert, do not safe if conversion fails
      rulesInput = routingContext.getBodyAsJson();
      Text2Drools.convert(rulesInput.getString("rulesAsText"),
        (policyType, policies, token) -> validatePolicy(
          existingPoliciesIds, policyType, policies, token));
    } catch (CirculationRulesException e) {
      processingError(routingContext.response(), e);
      return;
    } catch (DecodeException e) {
      decodingError(routingContext.response(), e);
      return;
    } catch (Exception e) {
      internalError(routingContext.response(), getStackTrace(e));
      return;
    }

    clients.circulationRulesStorage().put(rulesInput.copy())
      .thenApply(this::failWhenResponseOtherThanNoContent)
      .thenApply(result -> result.map(response -> noContent()))
      .thenAccept(webContext::writeResultToHttpResponse);

    CirculationRulesCache.getInstance().clearCache(webContext.getTenantId());
  }

  private Result<Response> failWhenResponseOtherThanNoContent(Result<Response> result) {
    return result.failWhen(
      response -> of(() -> response.getStatusCode() != 204),
      ForwardOnFailure::new);
  }

  private void validatePolicy(Map<String, Set<String>> existingPoliciesIds,
    String policyType, List<CirculationRulesParser.PolicyContext> policies, Token token) {

    if (!policyExists(existingPoliciesIds, policyType, policies)) {
       throw new CirculationRulesException(
         String.format("The policy %s does not exist", policyType),
         token.getLine(), token.getCharPositionInLine());
    }
  }

  private boolean policyExists(Map<String, Set<String>> existingPolicyIds,
    String policyType, List<CirculationRulesParser.PolicyContext> policies) {

    return MapUtils.isNotEmpty(existingPolicyIds) && isNotEmpty(existingPolicyIds.get(policyType))
        && existingPolicyIds.get(policyType).contains(
          policies.get(FIRST_ELEMENT_OF_LIST)
            .getChild(POLICY_ID_POSITION_NUMBER).getText());
  }

  private CompletableFuture<Result<Map<String, Set<String>>>> getPolicyIdsByType(Clients clients) {
    CollectionResourceClient loanPolicyClient = clients.loanPoliciesStorage();
    CollectionResourceClient noticePolicyClient = clients.patronNoticePolicesStorageClient();
    CollectionResourceClient requestPolicyClient = clients.requestPoliciesStorage();
    CollectionResourceClient overdueFinePolicyClient = clients.overdueFinesPoliciesStorage();
    CollectionResourceClient lostItemFeePolicyClient = clients.lostItemPoliciesStorage();
    Map<String, Set<String>> ids = new HashMap<>();

    return Result.ofAsync(() -> ids)
      .thenCombineAsync(
        getPolicyIdsByType(loanPolicyClient, "loanPolicies", "l"),
        (resultTotalIds, resultNewIds) -> resultTotalIds.combine(resultNewIds, this::getTotalMap))
      .thenCombineAsync(
        getPolicyIdsByType(noticePolicyClient, "patronNoticePolicies", "n"),
        (resultTotalIds, resultNewIds) -> resultTotalIds.combine(resultNewIds, this::getTotalMap))
      .thenCombineAsync(
        getPolicyIdsByType(requestPolicyClient, "requestPolicies", "r"),
        (resultTotalIds, resultNewIds) -> resultTotalIds.combine(resultNewIds, this::getTotalMap))
      .thenCombineAsync(
        getPolicyIdsByType(overdueFinePolicyClient, "overdueFinePolicies", "o"),
        (resultTotalIds, resultNewIds) -> resultTotalIds.combine(resultNewIds, this::getTotalMap))
      .thenCombineAsync(
        getPolicyIdsByType(lostItemFeePolicyClient, "lostItemFeePolicies", "i"),
        (resultTotalIds, resultNewIds) -> resultTotalIds.combine(resultNewIds, this::getTotalMap));
  }

  private Map<String, Set<String>> getTotalMap(Map<String, Set<String>> totalMap,
    Map<String, Set<String>> newMap) {

    totalMap.putAll(newMap);
    return totalMap;
  }

  private CompletableFuture<Result<Map<String, Set<String>>>> getPolicyIdsByType(
    CollectionResourceClient client, String entityName, String policyType) {

    return client.get(POLICY_PAGE_LIMIT)
      .thenApply(r -> r.next(response -> mapResponseToIds(response, entityName)))
      .thenApply(r -> r.map(MultipleRecords::getRecords))
      .thenApply(r -> r.map(collection -> collection.stream()
        .collect(groupingBy(id -> policyType, mapping(Function.identity(), toSet())))));
  }

  private Result<MultipleRecords<String>> mapResponseToIds(Response response, String entityName) {
    return MultipleRecords.from(response, v -> getProperty(v, "id"), entityName);
  }

  private static void processingError(HttpServerResponse response, CirculationRulesException e) {
    JsonObject body = new JsonObject();
    body.put("message", e.getMessage());
    body.put("line", e.getLine());
    body.put("column", e.getColumn());
    unprocessableEntity(body).writeTo(response);
  }

  private static void decodingError(HttpServerResponse response, DecodeException e) {
    JsonObject body = new JsonObject();
    body.put("message", e.getMessage());  // already contains line and column number
    unprocessableEntity(body).writeTo(response);
  }
}
