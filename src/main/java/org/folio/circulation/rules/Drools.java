package org.folio.circulation.rules;

import static org.folio.circulation.resources.AbstractCirculationRulesEngineResource.ITEM_TYPE_ID_NAME;
import static org.folio.circulation.resources.AbstractCirculationRulesEngineResource.LOAN_TYPE_ID_NAME;
import static org.folio.circulation.resources.AbstractCirculationRulesEngineResource.LOCATION_ID_NAME;
import static org.folio.circulation.resources.AbstractCirculationRulesEngineResource.PATRON_TYPE_ID_NAME;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;
import static org.folio.circulation.support.utils.LogUtil.asJson;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.core.event.DefaultAgendaEventListener;
import org.folio.circulation.domain.Location;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message.Level;
import org.kie.api.builder.ReleaseId;
import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;

import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Holds a Drools kieSession to calculate a loan policy.
 */
public class Drools {
  // https://docs.jboss.org/drools/release/6.2.0.CR1/drools-docs/html/ch19.html
  // http://www.deepakgaikwad.net/index.php/2016/05/16/drools-tutorial-beginners.html

  private static final Logger log = LogManager.getLogger(CirculationRulesProcessor.class);
  private final KieContainer kieContainer;

  /**
   * Create the Drools kieSession based on a String containing a drools file.
   * @param tenantId Used for releaseId, which helps to avoid cross-tenant concurrency issues.
   * @param drools A file in Drools syntax with the circulation rules.
   */
  public Drools(String tenantId, String drools) {
    // if KieServices.Factory.get() returns null add AppendingTransformer for META-INF/kie.conf
    // to maven-shade-plugin configuration (CIRC-309, CIRC-1147)
    KieServices kieServices = KieServices.Factory.get();

    // Creating tenant-specific releaseId. Using default release ID causes concurrency issues.
    ReleaseId releaseId = kieServices.newReleaseId("circulation-rules", tenantId, "1.0.0");

    KieFileSystem kfs = kieServices.newKieFileSystem().generateAndWritePomXML(releaseId);
    kfs.write("src/main/resources/circulationrules/circulation-rules.drl", drools);
    KieBuilder kieBuilder = kieServices.newKieBuilder(kfs);
    kieBuilder.buildAll();
    if (kieBuilder.getResults().hasMessages(Level.ERROR)) {
      throw new IllegalArgumentException("Drools build errors:\n" + kieBuilder.getResults().toString());
    }
    kieContainer = kieServices.newKieContainer(releaseId);
  }

  private KieSession createSession(MultiMap params, Location location, Match match) {
    log.debug("createSession:: parameters params: {}, location: {}, match: {}", params,
      location, match);
    String itemTypeId = params.get(ITEM_TYPE_ID_NAME);
    String loanTypeId = params.get(LOAN_TYPE_ID_NAME);
    String patronGroupId = params.get(PATRON_TYPE_ID_NAME);
    String locationId = params.get(LOCATION_ID_NAME);

    KieSession kieSession = kieContainer.newKieSession();
    kieSession.setGlobal("match", match);
    kieSession.insert(new ItemType(itemTypeId));
    kieSession.insert(new LoanType(loanTypeId));
    kieSession.insert(new PatronGroup(patronGroupId));
    kieSession.insert(new ItemLocation(locationId));
    if (location != null) {
      log.info("createSession:: location is not null");
      kieSession.insert(new Institution(location.getInstitutionId()));
      kieSession.insert(new Campus(location.getCampusId()));
      kieSession.insert(new Library(location.getLibraryId()));
    }

    return kieSession;
  }

  /**
   * Calculate the loan policy for itemTypeName and loanTypeName.
   * @param params request parameters
   * @param location - location with institution, library and campus
   * @return CirculationRuleMatch object with the name of the loan policy and rule conditions
   */
  public CirculationRuleMatch loanPolicy(MultiMap params, Location location) {
    log.debug("loanPolicy:: params params: {}, location: {}", params, location);
    final var match = new Match();
    final KieSession kieSession = createSession(params, location, match);
    final RuleEventListener ruleEventListener = new RuleEventListener();

    kieSession.addEventListener(ruleEventListener);
    kieSession.fireAllRules();
    kieSession.dispose();

    final Set<String> appliedRuleConditions = ruleEventListener.getRuleConditions();

    return new CirculationRuleMatch(match.loanPolicyId, new AppliedRuleConditions(
      isRuleItemTypePresent(appliedRuleConditions),
      isRuleLoanTypePresent(appliedRuleConditions),
      isRulePatronGroupPresent(appliedRuleConditions)));
  }

  /**
   * Return all loan policies calculated using the drools rules
   * in the order they match.
   * @param params request params
   * @param location - location with institution, library and campus
   * @return matches, each match has a loanPolicyId and a circulationRuleLine field
   */
  public JsonArray loanPolicies(MultiMap params, Location location) {
    log.debug("loanPolicies:: params params: {}, location: {}", params, location);
    final var match = new Match();
    final KieSession kieSession = createSession(params, location, match);

    JsonArray array = new JsonArray();

    while (kieSession.fireAllRules() > 0) {
      JsonObject json = new JsonObject();

      write(json, "loanPolicyId", match.loanPolicyId);
      write(json, "circulationRuleLine", match.lineNumber);

      array.add(json);
    }

    kieSession.dispose();
    log.info("loanPolicies:: result: {}", () -> asJson(array.stream().toList()));
    return array;
  }

  /**
   * Calculate the request policy for itemTypeName and loanType.
   * @param params request params
   * @param location - location with institution, library and campus
   * @return CirculationRuleMatch object with the name of the loan policy and rule conditions
   */
  public CirculationRuleMatch requestPolicy(MultiMap params, Location location) {
    log.debug("requestPolicy:: parameters params: {}, location: {}", params, location);
    final var match = new Match();
    final KieSession kieSession = createSession(params, location, match);

    kieSession.fireAllRules();
    kieSession.dispose();

    return new CirculationRuleMatch(match.requestPolicyId,
      new AppliedRuleConditions(false, false, false));
  }

   /**
   * Return all request policies calculated using the drools rules
   * in the order they match.
   * @param params request params
   * @param location - location with institution, library and campus
   * @return matches, each match has a requestPolicyId and a circulationRuleLine field
   */
  public JsonArray requestPolicies(MultiMap params, Location location) {
    log.debug("requestPolicy:: parameters params: {}, location: {}", params, location);

    final var match = new Match();
    final KieSession kieSession = createSession(params, location, match);

    JsonArray array = new JsonArray();

    while (kieSession.fireAllRules() > 0) {
      JsonObject json = new JsonObject();

      write(json, "requestPolicyId", match.requestPolicyId);
      write(json, "circulationRuleLine", match.lineNumber);

      array.add(json);
    }

    kieSession.dispose();

    log.info("requestPolicies:: result: {}", () -> asJson(array.stream().toList()));
    return array;
  }

  /**
   * Calculate the notice policy for itemTypeName and requestTypeName.
   * @param params request params
   * @param location - location with institution, library and campus
   * @return CirculationRuleMatch object with the name of the loan policy and rule conditions
   */
  public CirculationRuleMatch noticePolicy(MultiMap params, Location location) {
    log.debug("noticePolicy:: parameters params: {}, location: {}", params, location);
    final var match = new Match();
    final KieSession kieSession = createSession(params, location, match);

    kieSession.fireAllRules();
    kieSession.dispose();

    return new CirculationRuleMatch(match.noticePolicyId,
      new AppliedRuleConditions(false, false, false));
  }

   /**
   * Return all notice policies calculated using the drools rules
   * in the order they match.
   * @param params request params
   * @param location - location with institution, library and campus
   * @return matches, each match has a noticePolicyId and a circulationRuleLine field
   */
  public JsonArray noticePolicies(MultiMap params, Location location) {
    log.debug("noticePolicies:: parameters params: {}, location: {}", params, location);
    final var match = new Match();
    final KieSession kieSession = createSession(params, location, match);

    JsonArray array = new JsonArray();

    while (kieSession.fireAllRules() > 0) {
      JsonObject json = new JsonObject();

      json.put("noticePolicyId", match.noticePolicyId);
      write(json, "circulationRuleLine", match.lineNumber);

      array.add(json);
    }

    kieSession.dispose();
    log.info("noticePolicies:: result: {}", () -> asJson(array.stream().toList()));

    return array;
  }

  /**
   * Calculate the overdue fine policy for itemTypeName and requestTypeName.
   * @param params request params
   * @param location - location with institution, library and campus
   * @return CirculationRuleMatch object with the name of the loan policy and rule conditions
   */
  public CirculationRuleMatch overduePolicy(MultiMap params, Location location) {
    log.debug("overduePolicy:: parameters params: {}, location: {}", params, location);
    final var match = new Match();
    final KieSession kieSession = createSession(params, location, match);

    kieSession.fireAllRules();
    kieSession.dispose();

    return new CirculationRuleMatch(match.overduePolicyId,
      new AppliedRuleConditions(false, false, false));
  }

  /**
   * Return all overdue fine policies calculated using the drools rules
   * in the order they match.
   * @param params request params
   * @param location - location with institution, library and campus
   * @return matches, each match has a overduePolicyId and a circulationRuleLine field
   */
  public JsonArray overduePolicies(MultiMap params, Location location) {
    log.debug("overduePolicies:: parameters params: {}, location: {}", params, location);
    final var match = new Match();
    final KieSession kieSession = createSession(params, location, match);

    JsonArray array = new JsonArray();

    while (kieSession.fireAllRules() > 0) {
      JsonObject json = new JsonObject();

      write(json, "overduePolicyId", match.overduePolicyId);
      write(json, "circulationRuleLine", match.lineNumber);

      array.add(json);
    }

    kieSession.dispose();
    log.info("overduePolicies:: result: {}", () -> asJson(array.stream().toList()));

    return array;
  }

  /**
   * Calculate the lost item fee policy for itemTypeName and requestTypeName.
   * @param params request params
   * @param location - location with institution, library and campus
   * @return CirculationRuleMatch object with the name of the loan policy and rule conditions
   */
  public CirculationRuleMatch lostItemPolicy(MultiMap params, Location location) {
    log.debug("lostItemPolicy:: parameters params: {}, location: {}", params, location);

    final var match = new Match();
    final KieSession kieSession = createSession(params, location, match);

    kieSession.fireAllRules();
    kieSession.dispose();

    return new CirculationRuleMatch(match.lostItemPolicyId,
      new AppliedRuleConditions(false, false, false));
  }

  /**
   * Return all lost item fee policies calculated using the drools rules
   * in the order they match.
   * @param params request params
   * @param location - location with institution, library and campus
   * @return matches, each match has a lostItemPolicyId and a circulationRuleLine field
   */
  public JsonArray lostItemPolicies(MultiMap params, Location location) {
    log.debug("lostItemPolicies:: parameters params: {}, location: {}", params, location);
    final var match = new Match();
    final KieSession kieSession = createSession(params, location, match);

    JsonArray array = new JsonArray();

    while (kieSession.fireAllRules() > 0) {
      JsonObject json = new JsonObject();

      write(json, "lostItemPolicyId", match.lostItemPolicyId);
      write(json, "circulationRuleLine", match.lineNumber);

      array.add(json);
    }

    kieSession.dispose();
    log.info("lostItemPolicies:: result: {}", () -> asJson(array.stream().toList()));
    return array;
  }

  // NOTE: methods below used for testing

  /**
   * Return the loan policy calculated using the drools rules and the item type and loan type.
   * @param droolsFile - rules to use
   * @param params request parameters
   * @param location - location with institution, library and campus
   * @return loan policy
   */
  public static String loanPolicy(String droolsFile, MultiMap params, Location location) {
    return new Drools("test-tenant-id", droolsFile).loanPolicy(params, location).getPolicyId();
  }

  /**
   * Return the request policy calculated using the drools rules and the item type and request type.
   * @param droolsFile - rules to use
   * @param params request params
   * @param location - location with institution, library and campus
   * @return request policy
   */
  static String requestPolicy(String droolsFile, MultiMap params, Location location) {
    return new Drools("test-tenant-id", droolsFile).requestPolicy(params, location).getPolicyId();
  }

  private boolean isRuleItemTypePresent(Set<String> conditions) {
    return conditions.contains("ItemType");
  }

  private boolean isRuleLoanTypePresent(Set<String> conditions) {
    return conditions.contains("LoanType");
  }

  private boolean isRulePatronGroupPresent(Set<String> conditions) {
    return conditions.contains("PatronGroup");
  }

  private static class RuleEventListener extends DefaultAgendaEventListener {
    private Set<String> ruleConditionElements = Collections.emptySet();

    @Override
    public void afterMatchFired(AfterMatchFiredEvent event) {
      RuleImpl rule = (RuleImpl) event.getMatch().getRule();

      if (rule.getLhs() != null && rule.getLhs().getChildren() != null) {
        log.info("afterMatchFired:: getting rule conditions");
        ruleConditionElements = rule.getLhs().getChildren().stream()
          .map(Object::toString)
          .map(this::getRuleConditionFromStringRuleRepresentation)
          .collect(Collectors.toSet());
      }
    }

    public Set<String> getRuleConditions() {
      return Collections.unmodifiableSet(ruleConditionElements);
    }

    private String getRuleConditionFromStringRuleRepresentation(String stringRepresentation) {
      int endIndex = stringRepresentation.indexOf(']');
      int startIndex = stringRepresentation.lastIndexOf('.') + 1;

      return stringRepresentation.substring(startIndex, endIndex);
    }
  }
}
