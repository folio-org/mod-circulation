package org.folio.circulation.resources;

import static org.folio.circulation.rules.RulesExecutionParameters.forRequest;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.rules.CirculationRuleMatch;
import org.folio.circulation.rules.CirculationRulesProcessor;
import org.folio.circulation.rules.RulesExecutionParameters;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.http.server.ClientErrorResponse;
import org.folio.circulation.support.http.server.JsonHttpResponse;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.Result;

import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.val;

/**
 * The circulation rules engine calculates the loan policy based on
 * item type, loan type, patron type and location.
 */
public abstract class AbstractCirculationRulesEngineResource extends Resource {
  protected static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public static final String ITEM_TYPE_ID_NAME = "item_type_id";
  public static final String PATRON_TYPE_ID_NAME = "patron_type_id";
  public static final String LOCATION_ID_NAME = "location_id";
  public static final String LOAN_TYPE_ID_NAME = "loan_type_id";

  private final String applyPath;
  private final String applyAllPath;

  private final GetSinglePolicy singlePolicyGetter;
  private final GetAllPolicies allPoliciesGetter;

  /**
   * Create a circulation rules engine that listens at applyPath and applyAllPath.
   * @param applyPath  URL path for circulation rules triggering that returns the first match
   * @param applyAllPath  URL path for circulation rules triggering that returns all matches
   * @param client  the HttpClient to use for requests via Okapi
   */
  AbstractCirculationRulesEngineResource(String applyPath, String applyAllPath, HttpClient client,
    GetSinglePolicy getSinglePolicy, GetAllPolicies getAllPolicies) {

    super(client);
    this.applyPath = applyPath;
    this.applyAllPath = applyAllPath;
    this.allPoliciesGetter = getAllPolicies;
    this.singlePolicyGetter = getSinglePolicy;
  }

  /**
   * Register the paths set in the constructor.
   * @param router  where to register
   */
  @Override
  public void register(Router router) {
    router.get(applyPath   ).handler(this::apply);
    router.get(applyAllPath).handler(this::applyAll);
  }

  private boolean invalidUuid(HttpServerRequest request, String paramName) {
    log.debug("invalidUuid:: parameters paramName: {}", paramName);
    final String regex = "^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[1-5][a-fA-F0-9]{3}-[89abAB][a-fA-F0-9]{3}-[a-fA-F0-9]{12}$";
    String uuid = request.getParam(paramName);
    if (uuid == null) {
      log.debug("invalidUuid:: {} is null", paramName);
      ClientErrorResponse.badRequest(request.response(), "required query parameter missing: " + paramName);
      return true;
    }
    if (!uuid.matches(regex)) {
      log.debug("invalidUuid:: {} does not match the regex {}", uuid, regex);
      ClientErrorResponse.badRequest(request.response(), "invalid uuid format of " + paramName +
          ", expecting " + regex + " but it is " + uuid);
      return true;
    }
    return false;
  }

  private void apply(RoutingContext routingContext) {
    applyRules(routingContext, singlePolicyGetter::getPolicyIdAndRuleMatch, this::buildJsonResult);
  }

  private CompletableFuture<Result<JsonObject>> buildJsonResult(CirculationRuleMatch entity) {
    log.debug("buildJsonResult:: parameters entity: {}", entity);
    JsonObject appliedRuleConditions = new JsonObject()
      .put("materialTypeMatch", entity.getAppliedRuleConditions().isItemTypePresent())
      .put("loanTypeMatch", entity.getAppliedRuleConditions().isLoanTypePresent())
      .put("patronGroupMatch", entity.getAppliedRuleConditions().isPatronGroupPresent());

    return CompletableFuture.completedFuture(succeeded(new JsonObject()
      .put(getPolicyIdKey(), entity.getPolicyId())
      .put("appliedRuleConditions", appliedRuleConditions)
    ));
  }

  private void applyAll(RoutingContext routingContext) {
    applyRules(routingContext, allPoliciesGetter::getPolicies, this::buildJsonResult);
  }

  private CompletableFuture<Result<JsonObject>> buildJsonResult(JsonArray matches) {
    return CompletableFuture.completedFuture(succeeded(new JsonObject().put("circulationRuleMatches",
      matches)));
  }

  private <T> void applyRules(RoutingContext routingContext,
    BiFunction<CirculationRulesProcessor, RulesExecutionParameters, CompletableFuture<Result<T>>> triggerFunction,
    Function<T, CompletableFuture<Result<JsonObject>>> mapToJson) {

    val request = routingContext.request();

    if (invalidApplyParameters(request)) {
      log.debug("applyRules:: invalid apply parameters");
      return;
    }

    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);

    triggerFunction.apply(clients.circulationRulesProcessor(), forRequest(context))
      .thenCompose(r -> r.after(mapToJson))
      .thenApply(r -> r.map(JsonHttpResponse::ok))
      .thenAccept(context::writeResultToHttpResponse);
  }

  private boolean invalidApplyParameters(HttpServerRequest request) {
    return
        invalidUuid(request, ITEM_TYPE_ID_NAME) ||
        invalidUuid(request, LOAN_TYPE_ID_NAME) ||
        invalidUuid(request, PATRON_TYPE_ID_NAME) ||
        invalidUuid(request, LOCATION_ID_NAME);
  }

  protected abstract String getPolicyIdKey();

  @FunctionalInterface
  protected interface GetSinglePolicy {
    CompletableFuture<Result<CirculationRuleMatch>> getPolicyIdAndRuleMatch(
      CirculationRulesProcessor rulesProcessor, RulesExecutionParameters rulesExecutionParameters);
  }

  @FunctionalInterface
  protected interface GetAllPolicies {
    CompletableFuture<Result<JsonArray>> getPolicies(
      CirculationRulesProcessor rulesProcessor, RulesExecutionParameters rulesExecutionParameters);
  }
}
