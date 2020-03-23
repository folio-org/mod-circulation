package org.folio.circulation.rules;

import java.util.List;

import org.antlr.v4.runtime.Token;

@FunctionalInterface
public interface PolicyValidator {
  void validatePolicy(String policyType, List<CirculationRulesParser.PolicyContext> policies, Token token);
}
