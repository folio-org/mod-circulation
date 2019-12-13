package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.Result.succeeded;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.User;
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

    Item item = records.getLoan().getItem();
    User user = records.getLoan().getUser();

    return loanPolicyRepository.lookupLineNumber(item, user)
      .thenComposeAsync(r -> r.after(this::retrieveCirculationRule)
        .thenComposeAsync(result -> result.failAfter(rule -> isLimitReached(rule, records),
          limit -> itemLimitErrorFunction.apply(String.format("Patron has reached maximum limit of %d items", limit))))
        .thenApply(result -> result.map(v -> records)));
  }

  private CompletableFuture<Result<Boolean>> isLimitReached(String rule, LoanAndRelatedRecords records) {
    Item item = records.getLoan().getItem();
    String materialType = item.getMaterialType().getString("name");
    String loanType = item.getLoanTypeName();
    int itemLimit = records.getLoan().getLoanPolicy().getItemLimit();

    Predicate<Loan> isMaterialTypePresentInCirculationRule = loan -> isRuleMaterialTypePresent(rule)
        && loan.getItem().getMaterialType().getString("name").equals(materialType);
    Predicate<Loan> isLoanTypePresentInCirculationRule = loan -> isRuleLoanTypePresent(rule)
      && loan.getItem().getLoanTypeName().equals(loanType);

    if (isRuleMaterialTypePresent(rule) || isRuleLoanTypePresent(rule)) {

    }

    return loanRepository.findOpenLoansByUserIdAndLoanPolicyId(records)
      .thenApply(r -> r.map(loans -> loans.getRecords().stream()
          .filter(isMaterialTypePresentInCirculationRule)
          .filter(isLoanTypePresentInCirculationRule)))
      .thenApply(r -> r.map(loans -> loans.count() >= itemLimit));
  }

  private CompletableFuture<Result<String>> retrieveCirculationRule(int lineNumber) {
    return circulationRulesStorage.get()
      .thenApply(response -> {
//        if (response.getStatusCode() != 200) {
//          ForwardResponse.forward(routingContext.response(), response);
//          return completedFuture(failedDueToServerError("Unable to retrieve circulation rules"));
//        }
        JsonObject circulationRules = new JsonObject(response.getBody());
        String rulesAsText = circulationRules.getString("rulesAsText");
        return succeeded(rulesAsText);
      })
      .thenComposeAsync(r -> r.after(s -> getRuleByLineNumber(s, lineNumber)));
  }

  private CompletableFuture<Result<String>> getRuleByLineNumber(String rules, int lineNumber) {
    String line = Arrays.asList(rules.split("\n")).get(lineNumber - 1);

    return CompletableFuture.completedFuture(succeeded(line));
  }

  private boolean isRuleMaterialTypePresent(String rule) {
    return true;
  }

  private boolean isRuleLoanTypePresent(String rule) {
    return true;
  }
}
