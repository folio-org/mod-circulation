package org.folio.circulation.domain.validation;

import static java.util.concurrent.CompletableFuture.completedFuture;

import static org.folio.circulation.support.Result.ofAsync;
import static org.folio.circulation.support.Result.succeeded;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.policy.LoanPolicyRepository;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ValidationErrorFailure;

public class ItemLimitValidator {
  private final Function<String, ValidationErrorFailure> itemLimitErrorFunction;
  private final LoanRepository loanRepository;
  private final LoanPolicyRepository loanPolicyRepository;
  private final CollectionResourceClient circulationRulesStorage;
  private final RoutingContext routingContext;

  public ItemLimitValidator(Function<String, ValidationErrorFailure> itemLimitErrorFunction,
    LoanRepository loanRepository, LoanPolicyRepository loanPolicyRepository,
    CollectionResourceClient circulationRulesStorage, RoutingContext routingContext) {

    this.itemLimitErrorFunction = itemLimitErrorFunction;
    this.loanRepository = loanRepository;
    this.loanPolicyRepository = loanPolicyRepository;
    this.circulationRulesStorage = circulationRulesStorage;
    this.routingContext = routingContext;
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> refuseWhenItemLimitIsReached(
    LoanAndRelatedRecords records) {

    Loan loan = records.getLoan();
    Integer itemLimit = loan.getLoanPolicy().getItemLimit();

    if (itemLimit == null) {
      return completedFuture(succeeded(records));
    }

    return loanPolicyRepository.lookupLineNumber(loan.getItem(), loan.getUser())
      .thenComposeAsync(r -> r.after(this::retrieveCirculationRule)
        .thenComposeAsync(result -> result.failAfter(rule -> isLimitReached(rule, records),
          rule -> {
            String message = getErrorMessage(rule);
            return itemLimitErrorFunction.apply(String.format("Patron has reached maximum item limit of %d items %s",
              itemLimit, message));
          }))
        .thenApply(result -> result.map(v -> records)));
  }

  private CompletableFuture<Result<Boolean>> isLimitReached(String rule, LoanAndRelatedRecords records) {

    if (!isRuleMaterialTypePresent(rule) && !isRuleLoanTypePresent(rule)) {
      return ofAsync(() -> false);
    }

    Item item = records.getLoan().getItem();
    String materialType = item.getMaterialType().getString("name");
    String loanType = item.getLoanTypeName();
    Integer itemLimit = records.getLoan().getLoanPolicy().getItemLimit();

    if (isRuleMaterialTypePresent(rule) || isRuleLoanTypePresent(rule)) {
      return loanRepository.findOpenLoansByUserIdAndLoanPolicyIdWithItem(records)
        .thenApply(r -> r.map(loans -> loans.getRecords().stream()
            .filter(loan -> isMaterialTypeMatchInRetrievedLoan(materialType, loan))
            .filter(loan -> isLoanTypeMatchInRetrievedLoan(loanType, loan))
            .count()))
        .thenApply(r -> r.map(loansCount ->loansCount >= itemLimit));
    }

    return loanRepository.findOpenLoansByUserIdAndLoanPolicyId(records)
      .thenApply(r -> r.map(loans -> loans.getTotalRecords() >= itemLimit));
  }

  private boolean isMaterialTypeMatchInRetrievedLoan(String expectedMaterialType, Loan loan) {
    return expectedMaterialType.equals(loan.getItem().getMaterialType().getString("name"));
  }

  private boolean isLoanTypeMatchInRetrievedLoan(String expectedLoanType, Loan loan) {
    return expectedLoanType.equals(loan.getItem().getLoanTypeName());
  }

  private CompletableFuture<Result<String>> retrieveCirculationRule(int lineNumber) {
    return circulationRulesStorage.get()
      .thenApply(response -> {
        JsonObject circulationRules = new JsonObject(response.getBody());
        String rulesAsText = circulationRules.getString("rulesAsText");
        return succeeded(rulesAsText);
      })
      .thenComposeAsync(r -> r.after(s -> getRuleByLineNumber(s, lineNumber)));
  }

  private CompletableFuture<Result<String>> getRuleByLineNumber(String rules, int lineNumber) {
    String rule = Arrays.asList(rules.split("\n")).get(lineNumber - 1);

    return completedFuture(succeeded(rule));
  }

  private boolean isRuleMaterialTypePresent(String rule) {
    return splitRuleToMap(rule).get("m") != null;
  }

  private boolean isRuleLoanTypePresent(String rule) {
    return splitRuleToMap(rule).get("t") != null;
  }

  private boolean isRulePatronGroupPresent(String rule) {
    return splitRuleToMap(rule).get("g") != null;
  }

  private String getErrorMessage(String rule) {
    boolean isRuleMaterialTypePresent = isRuleMaterialTypePresent(rule);
    boolean isRuleLoanTypePresent = isRuleLoanTypePresent(rule);
    boolean isRulePatronGroupPresent = isRulePatronGroupPresent(rule);

    if (isRulePatronGroupPresent && isRuleMaterialTypePresent && isRuleLoanTypePresent) {
      return "for combination of patron group, material type and loan type";
    } else if (isRulePatronGroupPresent && isRuleMaterialTypePresent) {
      return "for combination of patron group and material type";
    } else if (isRulePatronGroupPresent && isRuleLoanTypePresent) {
      return "for combination of patron group and loan type";
    } else if (isRuleMaterialTypePresent && isRuleLoanTypePresent) {
      return "for combination of material type and loan type";
    } else if (isRuleMaterialTypePresent) {
      return "for material type";
    } else if (isRuleLoanTypePresent) {
      return "for loan type";
    }
    return StringUtils.EMPTY;
  }

  private Map<String, String> splitRuleToMap(String rule) {
    String partOfRuleBeforeColon = rule.split(":")[0];

    return Arrays.stream(partOfRuleBeforeColon.split("\\+"))
      .map(String::trim)
      .map(s -> s.split(StringUtils.SPACE))
      .collect(HashMap::new, (map, array) -> map.put(array[0],
        array.length > 1 ? array[1] : null), HashMap::putAll);
  }
}
