package org.folio.circulation.domain;

import org.folio.circulation.support.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.lang.invoke.MethodHandles;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

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
      requestAndRelatedRecords.getRequest().getString("proxyUserId"),
      requestAndRelatedRecords.getRequest().getString("requesterId"))
      .thenApply(result -> result.map(v -> requestAndRelatedRecords));
  }

  public CompletableFuture<HttpResult<LoanAndRelatedRecords>> refuseWhenInvalid(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    //TODO: Improve mapping back null result to records
    return refuseWhenInvalid(
      loanAndRelatedRecords.getLoan().getProxyUserId(),
      loanAndRelatedRecords.getLoan().getUserId())
      .thenApply(result -> result.map(v -> loanAndRelatedRecords));
  }

  private CompletableFuture<HttpResult<Void>> refuseWhenInvalid(
    String proxyUserId,
    String userId) {

    //No need to validate as not proxied activity
    if (proxyUserId == null) {
      return CompletableFuture.completedFuture(HttpResult.success(null));
    }

    String proxyRelationshipQuery = proxyRelationshipQuery(proxyUserId, userId);

    if(proxyRelationshipQuery == null) {
      return CompletableFuture.completedFuture(HttpResult.failure(
        new ServerErrorFailure("Unable to fetch proxy relationships")));
    }

    CompletableFuture<HttpResult<Void>> future = new CompletableFuture<>();

    proxyRelationshipsClient.getMany(proxyRelationshipQuery, 1000, 0, proxyValidResponse -> {
      if (proxyValidResponse != null) {
        if (proxyValidResponse.getStatusCode() != 200) {
          future.complete(HttpResult.failure(new ForwardOnFailure(proxyValidResponse)));
          return;
        }

        final MultipleRecordsWrapper proxyRelationships = MultipleRecordsWrapper.fromBody(
          proxyValidResponse.getBody(), "proxiesFor");

        final boolean activeRelationship = proxyRelationships.getRecords()
          .stream()
          .map(ProxyRelationship::new)
          .anyMatch(ProxyRelationship::isActive);

        future.complete(
          activeRelationship
            ? HttpResult.success(null)
            : HttpResult.failure(invalidRelationshipErrorSupplier.get()));
      }
      else {
        future.complete(HttpResult.failure(
          new ServerErrorFailure("No response when requesting proxies")));
      }
    });

    return future;
  }

  private String proxyRelationshipQuery(String proxyUserId, String sponsorUserId) {
    //Cannot check whether relationship is not active or expired in CQL, due to
    //having to look in two different parts of the representation for the properties
    //and CQL implementation does not currently support > comparison on optional properties

    String validateProxyQuery = String.format("proxyUserId=%s and userId=%s",
      proxyUserId, sponsorUserId);

    try {
      return URLEncoder.encode(validateProxyQuery, String.valueOf(StandardCharsets.UTF_8));
    } catch (UnsupportedEncodingException e) {
      log.error("Failed to encode query for proxies");
      return null;
    }
  }
}
