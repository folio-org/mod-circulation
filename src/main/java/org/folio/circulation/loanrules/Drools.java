package org.folio.circulation.loanrules;

import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message.Level;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;

public class Drools {
  // https://docs.jboss.org/drools/release/6.2.0.CR1/drools-docs/html/ch19.html
  // http://www.deepakgaikwad.net/index.php/2016/05/16/drools-tutorial-beginners.html

  /**
   * Create a kieSession based on a String containing a drools file.
   * <p>
   * Invoke dispose() after use!
   *
   * @param drools - the drools file
   * @return a KieSession
   */
  static public KieSession toSession(String drools) {
    KieServices kieServices = KieServices.Factory.get();
    KieFileSystem kfs = kieServices.newKieFileSystem();
    kfs.write("src/main/resources/loanrules/loan-rules.drl", drools);
    KieBuilder kieBuilder = kieServices.newKieBuilder(kfs);
    kieBuilder.buildAll();
    if (kieBuilder.getResults().hasMessages(Level.ERROR)) {
      throw new RuntimeException("Drools build errors:\n" + kieBuilder.getResults().toString());
    }
    KieContainer kieContainer = kieServices.newKieContainer(kieServices.getRepository().getDefaultReleaseId());
    return kieContainer.newKieSession();
  }

  /**
   * Return the loan policy calculated using the drools rules and the item type and loan type.
   * @param drools - rules to use
   * @param itemType - item (material) type
   * @param loanType - loan type
   * @return loan policy
   */
  static public String loanPolicy(String drools, String itemType, String loanType) {
    KieSession kieSession = toSession(drools);
    kieSession.insert(new ItemType(itemType));
    kieSession.insert(new LoanType(loanType));
    kieSession.fireAllRules();

    Iterable<? extends Object> objects = kieSession.getObjects();

    kieSession.dispose();

    for (Object o : objects) {
      if (o instanceof LoanPolicy) {
        return ((LoanPolicy) o).name;
      }
    }

    throw new IllegalStateException("LoanPolicy missing");
  }
}
