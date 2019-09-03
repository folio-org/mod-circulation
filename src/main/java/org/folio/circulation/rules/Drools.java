package org.folio.circulation.rules;

import static org.folio.circulation.resources.AbstractCirculationRulesEngineResource.INSTITUTION_ID_NAME;
import static org.folio.circulation.resources.AbstractCirculationRulesEngineResource.ITEM_TYPE_ID_NAME;
import static org.folio.circulation.resources.AbstractCirculationRulesEngineResource.LOAN_TYPE_ID_NAME;
import static org.folio.circulation.resources.AbstractCirculationRulesEngineResource.PATRON_TYPE_ID_NAME;
import static org.folio.circulation.resources.AbstractCirculationRulesEngineResource.LOCATION_ID_NAME;
import static org.folio.circulation.support.JsonPropertyWriter.write;

import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message.Level;
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

  private Match match = new Match();
  private KieContainer kieContainer;

  /**
   * Create the Drools kieSession based on a String containing a drools file.
   * @param drools A file in Drools syntax with the circulation rules.
   */
  public Drools(String drools) {
    KieServices kieServices = KieServices.Factory.get();
    KieFileSystem kfs = kieServices.newKieFileSystem();
    kfs.write("src/main/resources/circulationrules/circulation-rules.drl", drools);
    KieBuilder kieBuilder = kieServices.newKieBuilder(kfs);
    kieBuilder.buildAll();
    if (kieBuilder.getResults().hasMessages(Level.ERROR)) {
      throw new IllegalArgumentException("Drools build errors:\n" + kieBuilder.getResults().toString());
    }
    kieContainer = kieServices.newKieContainer(kieServices.getRepository().getDefaultReleaseId());
  }

  private KieSession createSession(MultiMap params) {
    String itemTypeId = params.get(ITEM_TYPE_ID_NAME);
    String loanTypeId = params.get(LOAN_TYPE_ID_NAME);
    String patronGroupId = params.get(PATRON_TYPE_ID_NAME);
    String shelvingLocationId = params.get(LOCATION_ID_NAME);
    String institutionLocationId = params.get(INSTITUTION_ID_NAME);

    KieSession kieSession = kieContainer.newKieSession();
    match.loanPolicyId = null;
    kieSession.setGlobal("match", match);
    kieSession.insert(new ItemType(itemTypeId));
    kieSession.insert(new LoanType(loanTypeId));
    kieSession.insert(new PatronGroup(patronGroupId));
    kieSession.insert(new Location(shelvingLocationId));
    kieSession.insert(new Institution(institutionLocationId));
    kieSession.insert(new Campus(""));
    kieSession.insert(new Library(""));
    return kieSession;
  }

  /**
   * Calculate the loan policy for itemTypeName and loanTypeName.
   * @param params request parameters
   * @return the name of the loan policy
   */
  public String loanPolicy(MultiMap params) {
    KieSession kieSession = createSession(params);
    kieSession.fireAllRules();
    kieSession.dispose();
    return match.loanPolicyId;
  }

  /**
   * Return all loan policies calculated using the drools rules
   * in the order they match.
   * @param params request params
   * @return matches, each match has a loanPolicyId and a circulationRuleLine field
   */
  public JsonArray loanPolicies(MultiMap params) {
    KieSession kieSession = createSession(params);

    JsonArray array = new JsonArray();

    while (kieSession.fireAllRules() > 0) {
      JsonObject json = new JsonObject();

      write(json, "loanPolicyId", match.loanPolicyId);
      writeLineMatch(json);

      array.add(json);
    }

    kieSession.dispose();
    return array;
  }

  /**
   * Calculate the request policy for itemTypeName and loanType.
   * @param params request params
   * @return the name of the request policy
   */
  public String requestPolicy(MultiMap params) {
    KieSession kieSession = createSession(params);
    kieSession.fireAllRules();
    kieSession.dispose();
    return match.requestPolicyId;
  }

   /**
   * Return all request policies calculated using the drools rules
   * in the order they match.
   * @param params request params
   * @return matches, each match has a requestPolicyId and a circulationRuleLine field
   */
  public JsonArray requestPolicies(MultiMap params) {
    KieSession kieSession = createSession(params);

    JsonArray array = new JsonArray();

    while (kieSession.fireAllRules() > 0) {
      JsonObject json = new JsonObject();

      write(json, "requestPolicyId", match.requestPolicyId);
      writeLineMatch(json);

      array.add(json);
    }

    kieSession.dispose();

    return array;
  }

  /**
   * Calculate the notice policy for itemTypeName and requestTypeName.
   * @param params request params
   * @return the name of the notice policy
   */
  public String noticePolicy(MultiMap params) {
    KieSession kieSession = createSession(params);
    kieSession.fireAllRules();
    kieSession.dispose();
    return match.noticePolicyId;
  }

   /**
   * Return all notice policies calculated using the drools rules
   * in the order they match.
   * @param params request params
   * @return matches, each match has a noticePolicyId and a circulationRuleLine field
   */
  public JsonArray noticePolicies(MultiMap params) {
    KieSession kieSession = createSession(params);

    JsonArray array = new JsonArray();

    while (kieSession.fireAllRules() > 0) {
      JsonObject json = new JsonObject();

      json.put("noticePolicyId", match.noticePolicyId);
      writeLineMatch(json);

      array.add(json);
    }

    kieSession.dispose();

    return array;
  }

  private void writeLineMatch(JsonObject json) {
    write(json, "circulationRuleLine", match.lineNumber);
  }

  // NOTE: methods below used for testing

  /**
   * Return the loan policy calculated using the drools rules and the item type and loan type.
   * @param droolsFile - rules to use
   * @param params request parameters
   * @return loan policy
   */
  public static String loanPolicy(String droolsFile, MultiMap params) {
    return new Drools(droolsFile).loanPolicy(params);
  }

  /**
   * Return the request policy calculated using the drools rules and the item type and request type.
   * @param droolsFile - rules to use
   * @param params request params
   * @return request policy
   */
  static String requestPolicy(String droolsFile, MultiMap params) {
    return new Drools(droolsFile).requestPolicy(params);
  }
}
