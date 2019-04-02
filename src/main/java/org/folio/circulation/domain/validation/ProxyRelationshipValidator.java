package org.folio.circulation.domain.validation;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.ProxyRelationship;
import org.folio.circulation.domain.UserRelatedRecord;
import org.folio.circulation.support.*;

import java.net.URLEncoder;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.Result.succeeded;

public class ProxyRelationshipValidator {
  private final CollectionResourceClient proxyRelationshipsClient;
  private Supplier<ValidationErrorFailure> invalidRelationshipErrorSupplier;

  public ProxyRelationshipValidator(
    Clients clients,
    Supplier<ValidationErrorFailure> invalidRelationshipErrorSupplier) {

    this.proxyRelationshipsClient = clients.userProxies();
    this.invalidRelationshipErrorSupplier = invalidRelationshipErrorSupplier;
  }

  public <T extends UserRelatedRecord> CompletableFuture<Result<T>> refuseWhenInvalid(
    T userRelatedRecord) {

    //No need to validate as not proxied activity
    if (userRelatedRecord.getProxyUserId() == null) {
      return completedFuture(succeeded(userRelatedRecord));
    }

    return succeeded(userRelatedRecord).failAfter(
      v -> doesNotHaveActiveProxyRelationship(userRelatedRecord),
        v -> invalidRelationshipErrorSupplier.get());
  }

  private CompletableFuture<Result<Boolean>> doesNotHaveActiveProxyRelationship(
    UserRelatedRecord record) {

    return proxyRelationshipQuery(record.getProxyUserId(), record.getUserId())
      .after(query -> proxyRelationshipsClient.getMany(query, 1000, 0)
        .thenApply(r -> MultipleRecords.from(r, ProxyRelationship::new, "proxiesFor")
        .map(MultipleRecords::getRecords)
        .map(relationships -> relationships.stream()
          .noneMatch(ProxyRelationship::isActive))));
  }

  private Result<String> proxyRelationshipQuery(
    String proxyUserId,
    String sponsorUserId) {

    String validateProxyQuery = String.format("proxyUserId==%s and userId==%s",
      proxyUserId, sponsorUserId);

    return Result.of(() ->
      URLEncoder.encode(validateProxyQuery, String.valueOf(UTF_8)));
  }
}
