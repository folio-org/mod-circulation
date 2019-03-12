package org.folio.circulation.domain.notice;

import static org.folio.circulation.support.HttpResult.succeeded;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.User;
import org.folio.circulation.support.CirculationRulesClient;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.ForwardOnFailure;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.SingleRecordFetcher;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.json.JsonObject;

public class PatronNoticePolicyRepository {

  private static final Logger log = LoggerFactory.getLogger(PatronNoticePolicyRepository.class);

  private final CirculationRulesClient circulationRulesClient;
  private final CollectionResourceClient patronNoticePolicesStorageClient;
  private final Function<JsonObject, HttpResult<PatronNoticePolicy>> patronNoticePolicyMapper;

  public PatronNoticePolicyRepository(Clients clients) {
    this(clients, new PatronNoticePolicyMapper());
  }

  public PatronNoticePolicyRepository(
    Clients clients,
    Function<JsonObject, HttpResult<PatronNoticePolicy>> patronNoticePolicyMapper) {
    this.circulationRulesClient = clients.circulationRules();
    this.patronNoticePolicesStorageClient = clients.patronNoticePolicesStorageClient();
    this.patronNoticePolicyMapper = patronNoticePolicyMapper;
  }

  public CompletableFuture<HttpResult<PatronNoticePolicy>> lookupNoticePolicy(Loan loan) {
    return lookupNoticePolicy(loan.getItem(), loan.getUser());
  }

  private CompletableFuture<HttpResult<PatronNoticePolicy>> lookupNoticePolicy(
    Item item, User user) {

    return lookupPatronNoticePolicyId(item, user)
      .thenComposeAsync(r -> r.after(this::lookupNoticePolicy))
      .thenApply(result -> result.next(patronNoticePolicyMapper));
  }


  private CompletableFuture<HttpResult<JsonObject>> lookupNoticePolicy(
    String noticePolicyId) {

    return SingleRecordFetcher.json(patronNoticePolicesStorageClient, "notice policy",
      response -> HttpResult.failed(new ServerErrorFailure(
        String.format("Notice policy %s could not be found, please check circulation rules", noticePolicyId))))
      .fetch(noticePolicyId);
  }

  private CompletableFuture<HttpResult<String>> lookupPatronNoticePolicyId(
    Item item,
    User user) {

    CompletableFuture<HttpResult<String>> findPatronNoticePolicyCompleted
      = new CompletableFuture<>();

    if (item.isNotFound()) {
      return CompletableFuture.completedFuture(HttpResult.failed(
        new ServerErrorFailure("Unable to apply circulation rules for unknown item")));
    }

    if (item.doesNotHaveHolding()) {
      return CompletableFuture.completedFuture(HttpResult.failed(
        new ServerErrorFailure("Unable to apply circulation rules for unknown holding")));
    }

    String loanTypeId = item.determineLoanTypeForItem();
    String locationId = item.getLocationId();

    String materialTypeId = item.getMaterialTypeId();

    String patronGroupId = user.getPatronGroupId();

    CompletableFuture<Response> circulationRulesResponse = new CompletableFuture<>();

    log.info(
      "Applying circulation rules for material type: {}, patron group: {}, loan type: {}, location: {}",
      materialTypeId, patronGroupId, loanTypeId, locationId);

    circulationRulesClient.applyRulesForNoticePolicy(loanTypeId, locationId, materialTypeId,
      patronGroupId, ResponseHandler.any(circulationRulesResponse));

    circulationRulesResponse.thenAcceptAsync(response -> {
      if (response.getStatusCode() == 404) {
        findPatronNoticePolicyCompleted.complete(HttpResult.failed(
          new ServerErrorFailure("Unable to apply circulation rules")));
      } else if (response.getStatusCode() != 200) {
        findPatronNoticePolicyCompleted.complete(HttpResult.failed(
          new ForwardOnFailure(response)));
      } else {
        String policyId = response.getJson().getString("noticePolicyId");
        findPatronNoticePolicyCompleted.complete(succeeded(policyId));
      }
    });

    return findPatronNoticePolicyCompleted;
  }
}
