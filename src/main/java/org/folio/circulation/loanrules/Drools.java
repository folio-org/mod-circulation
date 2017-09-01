package org.folio.circulation.loanrules;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message.Level;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.FactHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds a Drools kieSession to calculate a loan policy.
 */
public class Drools {
  // https://docs.jboss.org/drools/release/6.2.0.CR1/drools-docs/html/ch19.html
  // http://www.deepakgaikwad.net/index.php/2016/05/16/drools-tutorial-beginners.html

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private LoanPolicy loanPolicy = new LoanPolicy(null);
  private KieContainer kieContainer;

  /**
   * Create the Drools kieSession based on a String containing a drools file.
   */
  public Drools(String drools) {
    KieServices kieServices = KieServices.Factory.get();
    KieFileSystem kfs = kieServices.newKieFileSystem();
    kfs.write("src/main/resources/loanrules/loan-rules.drl", drools);
    KieBuilder kieBuilder = kieServices.newKieBuilder(kfs);
    kieBuilder.buildAll();
    if (kieBuilder.getResults().hasMessages(Level.ERROR)) {
      throw new RuntimeException("Drools build errors:\n" + kieBuilder.getResults().toString());
    }
    kieContainer = kieServices.newKieContainer(kieServices.getRepository().getDefaultReleaseId());
  }

  private KieSession createSession(String itemTypeName, String loanTypeName, String patronGroupName) {
    KieSession kieSession = kieContainer.newKieSession();
    loanPolicy.id = null;
    kieSession.setGlobal("loanPolicy", loanPolicy);
    kieSession.insert(new ItemType(itemTypeName));
    kieSession.insert(new LoanType(loanTypeName));
    kieSession.insert(new PatronGroup(patronGroupName));
    return kieSession;
  }

  /**
   * Calculate the loan policy for itemTypeName and loanTypeName.
   * @param itemTypeName the name of the item type
   * @param loanTypeName the name of the loan type
   * @param patronGroupName the name of the patron group
   * @return the name of the loan policy
   */
  public String loanPolicy(String itemTypeName, String loanTypeName, String patronGroupName) {
    KieSession kieSession = createSession(itemTypeName, loanTypeName, patronGroupName);
    kieSession.fireAllRules();
    kieSession.dispose();
    return loanPolicy.id;
  }

  /**
   * Return all loan policies calculated using the drools rules and the item type and loan type
   * in the order they match.
   * @param itemTypeName the name of the item type
   * @param loanTypeName the name of the loan type
   * @return the names of the loan policies
   */
  public List<String> loanPolicies(String itemTypeName, String loanTypeName, String patronGroupName) {
    KieSession kieSession = createSession(itemTypeName, loanTypeName, patronGroupName);
    List<String> list = new ArrayList<>();
    while (kieSession.fireAllRules() > 0) {
      list.add(loanPolicy.id);
    }
    kieSession.dispose();
    return list;
  }

  /**
   * Return the loan policy calculated using the drools rules and the item type and loan type.
   * @param droolsFile - rules to use
   * @param itemType - item (material) type name
   * @param loanType - loan type name
   * @return loan policy
   */
  static public String loanPolicy(String droolsFile, String itemType, String loanType, String patronGroupName) {
    return new Drools(droolsFile).loanPolicy(itemType, loanType, patronGroupName);
  }

  /**
   * Return all loan policies calculated using the drools rules and the item type and loan type
   * in the order they match.
   * @param droolsFile - rules to use
   * @param itemTypeName - item (material) type name
   * @param loanTypeName - loan type name
   * @return loan policies
   */
  static public List<String> loanPolicies(String droolsFile, String itemTypeName, String loanTypeName, String patronGroupName) {
    return new Drools(droolsFile).loanPolicies(itemTypeName, loanTypeName, patronGroupName);
  }
}
