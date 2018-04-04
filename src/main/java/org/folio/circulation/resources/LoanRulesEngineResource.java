package org.folio.circulation.resources;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.folio.circulation.loanrules.Drools;
import org.folio.circulation.loanrules.Text2Drools;
import org.folio.circulation.support.ClientUtil;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.server.ClientErrorResponse;
import org.folio.circulation.support.http.server.ForwardResponse;
import org.folio.circulation.support.http.server.JsonResponse;
import org.folio.circulation.support.http.server.ServerErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The loan rules engine calculates the loan policy based on
 * item type, loan type, patron type and shelving location.
 */
public class LoanRulesEngineResource extends CollectionResource {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final String ITEM_TYPE_ID_NAME = "item_type_id";
  private static final String LOAN_TYPE_ID_NAME = "loan_type_id";
  private static final String PATRON_TYPE_ID_NAME = "patron_type_id";
  private static final String SHELVING_LOCATION_ID_NAME = "shelving_location_id";

  private final String applyPath;
  private final String applyAllPath;

  /** after this time the rules get loaded before executing the loan rules engine */
  private static long maxAgeInMilliseconds = 5000;
  /** after this time the loan rules engine is executed first for a fast reply
   * and then the loan rules get reloaded */
  private static long triggerAgeInMilliseconds = 4000;

  private class Rules {
    String loanRulesAsTextFile = "";
    String loanRulesAsDrools = "";
    Drools drools;
    /** System.currentTimeMillis() of the last load/reload of the rules from the storage */
    long reloadTimestamp;
    boolean reloadInitiated = false;
  }
  /** rules and Drools for each tenantId */
  private static Map<String,Rules> rulesMap = new HashMap<>();

  /**
   * Set the cache time.
   * @param triggerAgeInMilliseconds  after this time the loan rules engine is executed first for a fast reply
   *                                  and then the loan rules get reloaded
   * @param maxAgeInMilliseconds  after this time the rules get loaded before executing the loan rules engine
   */
  public static void setCacheTime(long triggerAgeInMilliseconds, long maxAgeInMilliseconds) {
    LoanRulesEngineResource.triggerAgeInMilliseconds = triggerAgeInMilliseconds;
    LoanRulesEngineResource.maxAgeInMilliseconds = maxAgeInMilliseconds;
  }

  /**
   * Completely drop the cache. This enforces rebuilding the drools rules
   * even when the loan rules haven't changed.
   */
  public static void dropCache() {
    rulesMap.clear();
  }

  /**
   * Enforce reload of all loan rules of all tenants.
   * This doesn't rebuild the drools rules if the loan rules haven't changed.
   */
  public static void clearCache() {
    for (Rules rules: rulesMap.values()) {
      // timestamp in the past enforces reload
      rules.reloadTimestamp = 0;
    }
  }

  /**
   * Enforce reload of the tenant's loan rules.
   * This doesn't rebuild the drools rules if the loan rules haven't changed.
   * @param tenantId  id of the tenant
   */
  public static void clearCache(String tenantId) {
    Rules rules = rulesMap.get(tenantId);
    if (rules == null) {
      return;
    }
    rules.reloadTimestamp = 0;
  }

  /**
   * Create a loan rules engine that listens at applyPath and applyAllPath.
   * @param applyPath  URL path for loan rules triggering that returns the first match
   * @param applyAllPath  URL path for loan rules triggering that returns all matches
   * @param client
   */
  public LoanRulesEngineResource(String applyPath, String applyAllPath, HttpClient client) {
    super(client);
    this.applyPath = applyPath;
    this.applyAllPath = applyAllPath;
  }

  /**
   * Register the paths set in the constructor.
   * @param router  where to register
   */
  public void register(Router router) {
    router.get(applyPath   ).handler(this::apply);
    router.get(applyAllPath).handler(this::applyAll);
  }

  private String getTenantId(RoutingContext routingContext) {
    String tenantId = routingContext.request().getHeader("X-Okapi-Tenant");
    if (tenantId == null) {
      return "";
    }
    return tenantId;
  }

  private boolean isCurrent(Rules rules) {
    if (rules == null) {
      return false;
    }
    return rules.reloadTimestamp + maxAgeInMilliseconds > System.currentTimeMillis();
  }

  /**
   * Reload is needed if the last reload is TRIGGER_AGE_IN_MILLISECONDS old
   * and a reload hasn't been initiated yet.
   * @param rules - rules to reload
   * @return whether reload is needed
   */
  private boolean reloadNeeded(Rules rules) {
    if (rules.reloadInitiated) {
      return false;
    }
    return rules.reloadTimestamp + triggerAgeInMilliseconds < System.currentTimeMillis();
  }

  /**
   * Load the loan rules from the storage module.
   * @param tenantId - the tenant the rules are for
   * @param rules - where to store the rules and reload information
   * @param routingContext - where to report any error
   * @param done - invoked after success
   */
  public void reloadRules(Rules rules, RoutingContext routingContext, Handler<Void> done) {
    CollectionResourceClient loansRulesClient = ClientUtil.getLoanRulesClient(routingContext);
    if (loansRulesClient == null) {
      return;
    }

    loansRulesClient.get(response -> {
      try {
        if (response.getStatusCode() != 200) {
          ForwardResponse.forward(routingContext.response(), response);
          log.error(response.getStatusCode() + " " + response.getBody());
          return;
        }

        rules.reloadTimestamp = System.currentTimeMillis();
        rules.reloadInitiated = false;
        JsonObject loanRules = new JsonObject(response.getBody());
        log.debug("loanRules = {}", loanRules.encodePrettily());
        String loanRulesAsTextFile = loanRules.getString("loanRulesAsTextFile");
        if (loanRulesAsTextFile == null) {
          throw new NullPointerException("loanRulesAsTextFile");
        }
        if (rules.loanRulesAsTextFile.equals(loanRulesAsTextFile)) {
          done.handle(null);
          return;
        }
        rules.loanRulesAsTextFile = loanRulesAsTextFile;
        rules.loanRulesAsDrools = Text2Drools.convert(loanRulesAsTextFile);
        log.debug("loanRulesAsDrools = {}", rules.loanRulesAsDrools);
        rules.drools = new Drools(rules.loanRulesAsDrools);
        done.handle(null);
      }
      catch (Exception e) {
        log.error("reloadRules", e);
        if (routingContext.response().ended()) {
          return;
        }
        ServerErrorResponse.internalError(routingContext.response(), ExceptionUtils.getStackTrace(e));
      }
    });
  }

  /**
   * Return a Drools for the tenantId of the routingContext. On error send the
   * error message via the routingContext's response.
   * @param routingContext - where to get the tenantId and send any error message
   * @param droolsHandler - where to provide the Drools
   */
  private void drools(RoutingContext routingContext, Handler<Drools> droolsHandler) {
    try {
      String tenantId = getTenantId(routingContext);
      Rules rules = rulesMap.get(tenantId);
      if (isCurrent(rules)) {
        droolsHandler.handle(rules.drools);
        if (reloadNeeded(rules)) {
          rules.reloadInitiated = true;
          reloadRules(rules, routingContext, done -> {});
        }
        return;
      }

      if (rules == null) {
        rules = new Rules();
        rulesMap.put(tenantId, rules);
      }
      Rules finalRules = rules;

      reloadRules(rules, routingContext, done -> {
        try {
          droolsHandler.handle(finalRules.drools);
        } catch (Throwable e) {
          log.error("drools droolsHandler", e);
          ServerErrorResponse.internalError(routingContext.response(), ExceptionUtils.getStackTrace(e));
        }
      });
    } catch (Throwable e) {
      log.error("drools", e);
      ServerErrorResponse.internalError(routingContext.response(), ExceptionUtils.getStackTrace(e));
    }
  }

  private boolean invalidUuid(HttpServerRequest request, String paramName) {
    final String regex = "^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[1-5][a-fA-F0-9]{3}-[89abAB][a-fA-F0-9]{3}-[a-fA-F0-9]{12}$";
    String uuid = request.getParam(paramName);
    if (uuid == null) {
      ClientErrorResponse.badRequest(request.response(), "required query parameter missing: " + paramName);
      return true;
    }
    if (! uuid.matches(regex)) {
      ClientErrorResponse.badRequest(request.response(), "invalid uuid format of " + paramName +
          ", expecting " + regex + " but it is " + uuid);
      return true;
    }
    return false;
  }

  private boolean invalidApplyParameters(HttpServerRequest request) {
    return
        invalidUuid(request, ITEM_TYPE_ID_NAME) ||
        invalidUuid(request, LOAN_TYPE_ID_NAME) ||
        invalidUuid(request, PATRON_TYPE_ID_NAME) ||
        invalidUuid(request, SHELVING_LOCATION_ID_NAME);
  }

  private void apply(RoutingContext routingContext) {
    HttpServerRequest request = routingContext.request();
    if (invalidApplyParameters(request)) {
      return;
    }
    drools(routingContext, drools -> {
      try {
        String itemTypeId = request.getParam(ITEM_TYPE_ID_NAME);
        String loanTypeId = request.getParam(LOAN_TYPE_ID_NAME);
        String patronGroupId = request.getParam(PATRON_TYPE_ID_NAME);
        String shelvingLocationId = request.getParam(SHELVING_LOCATION_ID_NAME);
        String loanPolicyId = drools.loanPolicy(itemTypeId, loanTypeId, patronGroupId);
        JsonObject json = new JsonObject().put("loanPolicyId", loanPolicyId);
        JsonResponse.success(routingContext.response(), json);
      }
      catch (Exception e) {
        log.error("apply", e);
        ServerErrorResponse.internalError(routingContext.response(), ExceptionUtils.getStackTrace(e));
      }
    });
  }

  private void applyAll(RoutingContext routingContext) {
    HttpServerRequest request = routingContext.request();
    if (invalidApplyParameters(request)) {
      return;
    }
    if (routingContext.pathParam("loan_rules") != null) {
      ServerErrorResponse.internalError(routingContext.response(), "parameter loan_rules not implemented yet");
      return;
    }
    drools(routingContext, drools -> {
      try {
        String itemTypeId = request.getParam(ITEM_TYPE_ID_NAME);
        String loanTypeId = request.getParam(LOAN_TYPE_ID_NAME);
        String patronGroupId = request.getParam(PATRON_TYPE_ID_NAME);
        String shelvingLocationId = request.getParam(SHELVING_LOCATION_ID_NAME);
        List<String> loanPolicyIds = drools.loanPolicies(itemTypeId, loanTypeId, patronGroupId);
        JsonArray loanPolicies = new JsonArray();
        loanPolicyIds.forEach(id -> {
          JsonObject loanPolicy = new JsonObject().put("id", id);
          JsonObject match = new JsonObject().put("loanPolicy", loanPolicy);
          loanPolicies.add(match);
        });
        JsonObject json = new JsonObject()
            .put("loanRuleMatches", loanPolicies)
            .put("applyAll", "isNotCompletelyImplementedYet");
        JsonResponse.success(routingContext.response(), json);
      }
      catch (Exception e) {
        log.error("apply", e);
        ServerErrorResponse.internalError(routingContext.response(), ExceptionUtils.getStackTrace(e));
      }
    });
  }
}
