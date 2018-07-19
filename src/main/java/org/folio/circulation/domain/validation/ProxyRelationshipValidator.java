package org.folio.circulation.domain.validation;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.ProxyRelationship;
import org.folio.circulation.domain.UserRelatedRecord;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ValidationErrorFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.lang.invoke.MethodHandles;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.HttpResult.succeeded;

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

  public <T extends UserRelatedRecord> CompletableFuture<HttpResult<T>> refuseWhenInvalid(
    T userRelatedRecord) {

    //No need to validate as not proxied activity
    if (userRelatedRecord.getProxyUserId() == null) {
      return completedFuture(succeeded(userRelatedRecord));
    }

    return hasActiveProxyRelationship(userRelatedRecord)
        .thenApply(r -> r.next(found -> found
          ? succeeded(userRelatedRecord)
          : HttpResult.failed(invalidRelationshipErrorSupplier.get())));
  }

  private CompletableFuture<HttpResult<Boolean>> hasActiveProxyRelationship(
    UserRelatedRecord userRelatedRecord) {

    String proxyRelationshipQuery = proxyRelationshipQuery(
      userRelatedRecord.getProxyUserId(), userRelatedRecord.getUserId());

    return proxyRelationshipsClient.getMany(proxyRelationshipQuery, 1000, 0)
      .thenApply(response -> MultipleRecords.from(response, ProxyRelationship::new, "proxiesFor")
        .map(MultipleRecords::getRecords)
        .map(relationships -> relationships.stream().anyMatch(ProxyRelationship::isActive)));
  }

  private String proxyRelationshipQuery(String proxyUserId, String sponsorUserId) {
    //Cannot check whether relationship is not active or expired in CQL, due to
    //having to look in two different parts of the representation for the properties
    //and CQL implementation does not currently support > comparison on optional properties

    String validateProxyQuery = String.format("proxyUserId==%s and userId==%s",
      proxyUserId, sponsorUserId);

    try {
      return URLEncoder.encode(validateProxyQuery, String.valueOf(StandardCharsets.UTF_8));
    } catch (UnsupportedEncodingException e) {
      log.error("Failed to encode query for proxies");
      return null;
    }
  }
}
