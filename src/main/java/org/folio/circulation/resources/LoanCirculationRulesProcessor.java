package org.folio.circulation.resources;

import org.folio.circulation.domain.Location;
import org.folio.circulation.rules.CirculationRuleMatch;
import org.folio.circulation.rules.Drools;

import io.vertx.core.MultiMap;

public class LoanCirculationRulesProcessor {

  public static CirculationRuleMatch getLoanPolicyAndMatch(Drools drools, MultiMap params, Location location) {
    return drools.loanPolicy(params, location);
  }
}
