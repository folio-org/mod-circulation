package org.folio.circulation.resources;

import io.vertx.core.Handler;
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
import org.folio.circulation.support.http.server.ForwardResponse;
import org.folio.circulation.support.http.server.JsonResponse;
import org.folio.circulation.support.http.server.ServerErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LoanRulesEngineResource {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final String applyPath;
  private final String applyAllPath;

  /** after this time the rules get loaded before executing the loan rules engine */
  static long MAX_AGE_IN_MILLISECONDS = 5000;
  /** after this time the loan rules engine is executed first and the the loan rules get reloaded */
  static long TRIGGER_AGE_IN_MILLISECONDS = 4000;

  private class Rules {
    String loanRulesAsTextFile = "";
    String loanRulesAsDrools = "";
    Drools drools;
    /** System.currentTimeMillis() of the last load/reload of the rules from the storage */
    long reloadTimestamp;
    boolean reloadInitiated = false;
  };
  /** rules and Drools for each tenantId */
  static private Map<String,Rules> rulesMap = new HashMap<>();

  /**
   * Enforce reload of all loan rules of all tenants
   */
  static public void clearCache() {
    rulesMap.clear();
  }

  public LoanRulesEngineResource(String applyPath, String applyAllPath) {
    this.applyPath = applyPath;
    this.applyAllPath = applyAllPath;
  }

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
    return rules.reloadTimestamp + MAX_AGE_IN_MILLISECONDS > System.currentTimeMillis();
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
    return rules.reloadTimestamp + TRIGGER_AGE_IN_MILLISECONDS < System.currentTimeMillis();
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
      catch (Throwable e) {
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

  private void apply(RoutingContext routingContext) {
    drools(routingContext, drools -> {
      try {
        HttpServerRequest request = routingContext.request();
        String itemTypeId = request.getParam("item_type_id");
        String loanTypeId = request.getParam("loan_type_id");
        String patronGroupId = request.getParam("patron_type_id");
        String shelvingLocationId = request.getParam("shelving_location_id");
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
    if (routingContext.pathParam("loan_rules") == null) {
      ServerErrorResponse.internalError(routingContext.response(), "parameter loan_rules not implemented");
      return;
    }
    drools(routingContext, drools -> {
      try {
        HttpServerRequest request = routingContext.request();
        String itemTypeId = request.getParam("item_type_id");
        String loanTypeId = request.getParam("loan_type_id");
        String patronGroupId = request.getParam("patron_type_id");
        String shelvingLocationId = request.getParam("shelving_location_id");
        List<String> loanPolicyIds = drools.loanPolicies(itemTypeId, loanTypeId, patronGroupId);
        Map<String,JsonObject> loanPolicyMap = new HashMap<>();
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
