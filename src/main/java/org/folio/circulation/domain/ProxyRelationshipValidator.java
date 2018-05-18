package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.*;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.lang.invoke.MethodHandles;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ProxyRelationshipValidator {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final CollectionResourceClient proxyRelationshipsClient;
  private Supplier<ValidationErrorFailure> invalidRelationshipErrorSupplier;

  public ProxyRelationshipValidator(
    Clients clients,
    Supplier<ValidationErrorFailure> invalidRelationshipErrorSupplier) {

    this.proxyRelationshipsClient = clients.userProxies();
    this.invalidRelationshipErrorSupplier = invalidRelationshipErrorSupplier;
  }

  public CompletableFuture<HttpResult<RequestAndRelatedRecords>> refuseWhenInvalid(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    //TODO: Improve mapping back null result to records
    return refuseWhenInvalid(
      requestAndRelatedRecords.request.getString("proxyUserId"),
      requestAndRelatedRecords.request.getString("requesterId"))
      .thenApply(result -> result.map(v -> requestAndRelatedRecords));
  }

  public CompletableFuture<HttpResult<LoanAndRelatedRecords>> refuseWhenInvalid(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    //TODO: Improve mapping back null result to records
    return refuseWhenInvalid(
      loanAndRelatedRecords.loan.getProxyUserId(),
      loanAndRelatedRecords.loan.getUserId())
      .thenApply(result -> result.map(v -> loanAndRelatedRecords));
  }

  private CompletableFuture<HttpResult<Void>> refuseWhenInvalid(
    String proxyUserId,
    String userId) {

    String validProxyQuery = buildIsValidUserProxyQuery(
      proxyUserId,
      userId);

    if(validProxyQuery == null) {
      return CompletableFuture.completedFuture(HttpResult.success(null));
    }

    CompletableFuture<HttpResult<Void>> future = new CompletableFuture<>();

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
          future.complete(HttpResult.success(null));
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

  private static String buildIsValidUserProxyQuery(String proxyUserId, String sponsorUserId){
    if(proxyUserId != null) {
//      DateTime expDate = new DateTime(DateTimeZone.UTC);
      String validateProxyQuery ="proxyUserId="+ proxyUserId
        +" and userId="+sponsorUserId
        +" and meta.status=Active";
      //Temporarily removed as does not work when optional expiration date is missing
//          +" and meta.expirationDate>"+expDate.toString().trim();
      try {
        return URLEncoder.encode(validateProxyQuery, String.valueOf(StandardCharsets.UTF_8));
      } catch (UnsupportedEncodingException e) {
        log.error("Failed to encode query for proxies");
        return null;
      }
    }
    return null;
  }
}
