package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.*;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ProxyRelationshipValidator {
  private final CollectionResourceClient proxyRelationshipsClient;
  private Supplier<ValidationErrorFailure> invalidRelationshipErrorSupplier;

  public ProxyRelationshipValidator(
    Clients clients,
    Supplier<ValidationErrorFailure> invalidRelationshipErrorSupplier) {

    this.proxyRelationshipsClient = clients.userProxies();
    this.invalidRelationshipErrorSupplier = invalidRelationshipErrorSupplier;
  }

  public CompletableFuture<HttpResult<LoanAndRelatedRecords>> refuseWhenInvalid(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    String validProxyQuery = CqlHelper.buildIsValidUserProxyQuery(
      loanAndRelatedRecords.loan.getProxyUserId(),
      loanAndRelatedRecords.loan.getUserId());

    if(validProxyQuery == null) {
      return CompletableFuture.completedFuture(HttpResult.success(loanAndRelatedRecords));
    }

    CompletableFuture<HttpResult<LoanAndRelatedRecords>> future = new CompletableFuture<>();

    handleProxy(proxyRelationshipsClient, validProxyQuery, proxyValidResponse -> {
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

        if (unExpiredRelationships.isEmpty()) {
          future.complete(HttpResult.failure(invalidRelationshipErrorSupplier.get()));
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

  private void handleProxy(
    CollectionResourceClient client,
    String query,
    Consumer<Response> responseHandler) {

    if(query != null){
      client.getMany(query, 1, 0, responseHandler);
    }
    else{
      responseHandler.accept(null);
    }
  }
}
