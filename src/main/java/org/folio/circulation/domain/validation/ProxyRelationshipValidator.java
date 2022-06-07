package org.folio.circulation.domain.validation;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.failures.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.ProxyRelationship;
import org.folio.circulation.domain.UserRelatedRecord;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.GetManyRecordsClient;
import org.folio.circulation.support.failures.ValidationErrorFailure;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.results.Result;

public class ProxyRelationshipValidator {
  private final GetManyRecordsClient proxyRelationshipsClient;
  private final Supplier<ValidationErrorFailure> invalidRelationshipErrorSupplier;

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

    if (StringUtils.equals(userRelatedRecord.getProxyUserId(), userRelatedRecord.getUserId())) {
      return completedFuture(failed(singleValidationError(
        "User cannot be proxy for themself", "proxyUserId",
        userRelatedRecord.getProxyUserId())));
    }

    return succeeded(userRelatedRecord).failAfter(
      v -> doesNotHaveActiveProxyRelationship(userRelatedRecord),
        v -> invalidRelationshipErrorSupplier.get());
  }

  private CompletableFuture<Result<Boolean>> doesNotHaveActiveProxyRelationship(
    UserRelatedRecord record) {

    return proxyRelationshipQuery(record.getProxyUserId(), record.getUserId())
      .after(query -> proxyRelationshipsClient.getMany(query, PageLimit.oneThousand())
      .thenApply(result -> result.next(
        response -> MultipleRecords.from(response, ProxyRelationship::new, "proxiesFor"))
      .map(MultipleRecords::getRecords)
        .map(relationships -> relationships.stream()
          .noneMatch(ProxyRelationship::isActive))));
  }

  private Result<CqlQuery> proxyRelationshipQuery(
    String proxyUserId, String sponsorUserId) {

    final Result<CqlQuery> proxyUserIdQuery = exactMatch("proxyUserId", proxyUserId);
    final Result<CqlQuery> userIdQuery = exactMatch("userId", sponsorUserId);

    return proxyUserIdQuery.combine(userIdQuery, CqlQuery::and);
  }
}
