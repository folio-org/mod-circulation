package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.*;
import org.joda.time.DateTime;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class ProxyRelationshipValidator {
  private final CollectionResourceClient proxyRelationshipsClient;

  public ProxyRelationshipValidator(Clients clients) {
    proxyRelationshipsClient = clients.userProxies();
  }

  public CompletableFuture<HttpResult<LoanAndRelatedRecords>> refuseWhenProxyRelationshipIsInvalid(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    String validProxyQuery = CqlHelper.buildIsValidUserProxyQuery(
      loanAndRelatedRecords.loan.getString("proxyUserId"),
      loanAndRelatedRecords.loan.getString("userId"));

    if(validProxyQuery == null) {
      return CompletableFuture.completedFuture(HttpResult.success(loanAndRelatedRecords));
    }

    CompletableFuture<HttpResult<LoanAndRelatedRecords>> future = new CompletableFuture<>();

    LoanValidation.handleProxy(proxyRelationshipsClient, validProxyQuery, proxyValidResponse -> {
      if (proxyValidResponse != null) {
        if (proxyValidResponse.getStatusCode() != 200) {
          future.complete(HttpResult.failure(new ForwardOnFailure(proxyValidResponse)));
          return;
        }

        final MultipleRecordsWrapper proxyRelationships = MultipleRecordsWrapper.fromBody(
          proxyValidResponse.getBody(), "proxiesFor");

        final Collection<JsonObject> unExpiredRelationships = proxyRelationships.getRecords()
          .stream()
          .filter(relationship -> {
            if(relationship.containsKey("meta")) {
              final JsonObject meta = relationship.getJsonObject("meta");

              if(meta.containsKey("expirationDate")) {
                final DateTime expirationDate = DateTime.parse(
                  meta.getString("expirationDate"));

                return expirationDate.isAfter(DateTime.now());
              }
              else {
                return true;
              }
            }
            else {
              return true;
            }
          })
          .collect(Collectors.toList());

        if (unExpiredRelationships.isEmpty()) { //if empty then we dont have a valid proxy id in the loan
          future.complete(HttpResult.failure(new ValidationErrorFailure(
            "proxyUserId is not valid", "proxyUserId",
            loanAndRelatedRecords.loan.getString("proxyUserId"))));
        }
        else {
          future.complete(HttpResult.success(loanAndRelatedRecords));
        }
      }
      else {
        future.complete(HttpResult.failure(
          new ServerErrorFailure("No response when requesting proxies")));
      }
    });

    return future;
  }
}
