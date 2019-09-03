package org.folio.circulation.resources;

import static org.folio.circulation.support.http.server.ServerErrorResponse.internalError;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.folio.circulation.rules.Drools;
import org.folio.circulation.rules.Text2Drools;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.OkJsonResponseResult;
import org.folio.circulation.support.http.server.ClientErrorResponse;
import org.folio.circulation.support.http.server.ForwardResponse;
import org.folio.circulation.support.http.server.WebContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * The circulation rules engine calculates the loan policy based on
 * item type, loan type, patron type, location and institution.
 */
public abstract class AbstractCirculationRulesEngineResource extends Resource {
  protected static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public static final String ITEM_TYPE_ID_NAME = "item_type_id";
  public static final String PATRON_TYPE_ID_NAME = "patron_type_id";
  public static final String LOCATION_ID_NAME = "location_id";
  public static final String LOAN_TYPE_ID_NAME = "loan_type_id";
  public static final String INSTITUTION_ID_NAME = "institution_id";

  private final String applyPath;
  private final String applyAllPath;

  /** after this time the rules get loaded before executing the circulation rules engine */
  private static long maxAgeInMilliseconds = 5000;
  /** after this time the circulation rules engine is executed first for a fast reply
   * and then the circulation rules get reloaded */
  private static long triggerAgeInMilliseconds = 4000;

  private class Rules {
    String rulesAsText = "";
    String rulesAsDrools = "";
    Drools drools;
    /** System.currentTimeMillis() of the last load/reload of the rules from the storage */
    long reloadTimestamp;
    boolean reloadInitiated = false;
  }
  /** rules and Drools for each tenantId */
  private static Map<String,Rules> rulesMap = new HashMap<>();

  /**
   * Set the cache time.
   * @param triggerAgeInMilliseconds  after this time the circulation rules engine is executed first for a fast reply
   *                                  and then the circulation rules get reloaded
   * @param maxAgeInMilliseconds  after this time the rules get loaded before executing the circulation rules engine
   */
  public static void setCacheTime(long triggerAgeInMilliseconds, long maxAgeInMilliseconds) {
    AbstractCirculationRulesEngineResource.triggerAgeInMilliseconds = triggerAgeInMilliseconds;
    AbstractCirculationRulesEngineResource.maxAgeInMilliseconds = maxAgeInMilliseconds;
  }

  /**
   * Completely drop the cache. This enforces rebuilding the drools rules
   * even when the circulation rules haven't changed.
   */
  public static void dropCache() {
    rulesMap.clear();
  }

  /**
   * Enforce reload of the tenant's circulation rules.
   * This doesn't rebuild the drools rules if the circulation rules haven't changed.
   * @param tenantId  id of the tenant
   */
  static void clearCache(String tenantId) {
    Rules rules = rulesMap.get(tenantId);
    if (rules == null) {
      return;
    }
    rules.reloadTimestamp = 0;
  }

  /**
   * Create a circulation rules engine that listens at applyPath and applyAllPath.
   * @param applyPath  URL path for circulation rules triggering that returns the first match
   * @param applyAllPath  URL path for circulation rules triggering that returns all matches
   * @param client  the HttpClient to use for requests via Okapi
   */
  AbstractCirculationRulesEngineResource(String applyPath, String applyAllPath, HttpClient client) {
    super(client);
    this.applyPath = applyPath;
    this.applyAllPath = applyAllPath;
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

  private String getTenantId(RoutingContext routingContext) {
    return new WebContext(routingContext).getTenantId();
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
   * Load the circulation rules from the storage module.
   * @param rules - where to store the rules and reload information
   * @param routingContext - where to report any error
   * @param done - invoked after success
   */
  private void reloadRules(Rules rules, RoutingContext routingContext, Handler<Void> done) {
    final Clients clients = Clients.create(new WebContext(routingContext), client);
    CollectionResourceClient circulationRulesClient = clients.circulationRulesStorage();

    if (circulationRulesClient == null) {
      return;
    }

    circulationRulesClient.get().thenAccept(response -> {
      try {
        if (response.getStatusCode() != 200) {
          ForwardResponse.forward(routingContext.response(), response);
          log.error("{} {}", response.getStatusCode(), response.getBody());
          return;
        }

        rules.reloadTimestamp = System.currentTimeMillis();
        rules.reloadInitiated = false;
        JsonObject circulationRules = new JsonObject(response.getBody());
        if (log.isDebugEnabled()) {
          log.debug("circulationRules = {}", circulationRules.encodePrettily());
        }
        String rulesAsText = circulationRules.getString("rulesAsText");
        if (rulesAsText == null) {
          throw new NullPointerException("rulesAsText");
        }
        if (rules.rulesAsText.equals(rulesAsText)) {
          done.handle(null);
          return;
        }
        rules.rulesAsText = rulesAsText;
        rules.rulesAsDrools = Text2Drools.convert(rulesAsText);
        log.debug("rulesAsDrools = {}", rules.rulesAsDrools);
        rules.drools = new Drools(rules.rulesAsDrools);
        done.handle(null);
      }
      catch (Exception e) {
        log.error("reloadRules", e);
        if (routingContext.response().ended()) {
          return;
        }
        internalError(routingContext.response(), ExceptionUtils.getStackTrace(e));
      }
    });
  }

  /**
   * Return a Drools for the tenantId of the routingContext. On error send the
   * error message via the routingContext's response.
   * @param routingContext - where to get the tenantId and send any error message
   * @param droolsHandler - where to provide the Drools
   */
  protected void drools(RoutingContext routingContext, Handler<Drools> droolsHandler) {
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
        } catch (Exception e) {
          log.error("drools droolsHandler", e);
          internalError(routingContext.response(), ExceptionUtils.getStackTrace(e));
        }
      });
    } catch (Exception e) {
      log.error("drools", e);
      internalError(routingContext.response(), ExceptionUtils.getStackTrace(e));
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

  private void apply(RoutingContext routingContext) {
    HttpServerRequest request = routingContext.request();
    if (invalidApplyParameters(request)) {
      return;
    }
    drools(routingContext, drools -> {
      try {
        String policyId = getPolicyId(request.params(), drools);
        JsonObject json = new JsonObject().put(getPolicyIdKey(), policyId);

        new OkJsonResponseResult(json)
          .writeTo(routingContext.response());
      }
      catch (Exception e) {
        log.error("apply notice policy", e);
        internalError(routingContext.response(), ExceptionUtils.getStackTrace(e));
      }
    });
  }

  private void applyAll(RoutingContext routingContext, Drools drools) {
    HttpServerRequest request = routingContext.request();
    if (invalidApplyParameters(request)) {
      return;
    }
    try {
      JsonArray matches = getPolicies(request.params(), drools);
      JsonObject json = new JsonObject().put("circulationRuleMatches", matches);

      new OkJsonResponseResult(json)
        .writeTo(routingContext.response());
    }
    catch (Exception e) {
      log.error("applyAll", e);
      internalError(routingContext.response(), ExceptionUtils.getStackTrace(e));
    }
  }

  private void applyAll(RoutingContext routingContext) {
    String circulationRules = routingContext.pathParam("circulation_rules");
    if (circulationRules == null) {
      drools(routingContext, drools -> applyAll(routingContext, drools));
      return;
    }

    try {
      String droolsFile = Text2Drools.convert(circulationRules);
      Drools drools = new Drools(droolsFile);
      applyAll(routingContext, drools);
    }
    catch (Exception e) {
      log.error("applyAll", e);
      internalError(routingContext.response(), ExceptionUtils.getStackTrace(e));
    }
  }

  private boolean invalidApplyParameters(HttpServerRequest request) {
    return
        invalidUuid(request, ITEM_TYPE_ID_NAME) ||
        invalidUuid(request, LOAN_TYPE_ID_NAME) ||
        invalidUuid(request, PATRON_TYPE_ID_NAME) ||
        invalidUuid(request, LOCATION_ID_NAME)  ||
        invalidUuid(request, INSTITUTION_ID_NAME);
  }

  protected abstract String getPolicyId(MultiMap params, Drools drools);

  protected abstract String getPolicyIdKey();

  protected abstract JsonArray getPolicies(MultiMap params, Drools drools);
}
