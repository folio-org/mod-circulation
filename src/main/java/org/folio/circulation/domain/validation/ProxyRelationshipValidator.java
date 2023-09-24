package org.folio.circulation.domain.validation;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.ErrorCode.USER_CANNOT_BE_PROXY_FOR_THEMSELVES;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.ProxyRelationship;
import org.folio.circulation.domain.UserRelatedRecord;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.GetManyRecordsClient;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.results.Result;

public class ProxyRelationshipValidator {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

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

    log.debug("refuseWhenInvalid:: parameters userRelatedRecord");

    //No need to validate as not proxied activity
    if (userRelatedRecord.getProxyUserId() == null) {
      log.info("refuseWhenInvalid:: proxy user ID is null");
      return completedFuture(succeeded(userRelatedRecord));
    }

    if (StringUtils.equals(userRelatedRecord.getProxyUserId(), userRelatedRecord.getUserId())) {
      log.info("refuseWhenInvalid:: proxy user ID is equal to user ID");
      return completedFuture(failed(singleValidationError(
        "User cannot be proxy for themselves", "proxyUserId",
        userRelatedRecord.getProxyUserId(), USER_CANNOT_BE_PROXY_FOR_THEMSELVES)));
    }

    return succeeded(userRelatedRecord).failAfter(
      v -> doesNotHaveActiveProxyRelationship(userRelatedRecord),
        v -> invalidRelationshipErrorSupplier.get());
  }

  private CompletableFuture<Result<Boolean>> doesNotHaveActiveProxyRelationship(
    UserRelatedRecord record) {

    log.debug("doesNotHaveActiveProxyRelationship:: parameters record: {}", record);

    return proxyRelationshipQuery(record.getProxyUserId(), record.getUserId())
      .after(query -> proxyRelationshipsClient.getMany(query, PageLimit.oneThousand())
      .thenApply(result -> result.next(
        response -> MultipleRecords.from(response, ProxyRelationship::new, "proxiesFor"))
      .map(MultipleRecords::getRecords)
        .map(relationships -> relationships.stream()
          .noneMatch(ProxyRelationship::isActive))));
  }

  private Result<CqlQuery> proxyRelationshipQuery(String proxyUserId, String sponsorUserId) {
    log.debug("proxyRelationshipQuery:: parameters proxyUserId: {}, sponsorUserId: {}", proxyUserId,
      sponsorUserId);

    final Result<CqlQuery> proxyUserIdQuery = exactMatch("proxyUserId", proxyUserId);
    final Result<CqlQuery> userIdQuery = exactMatch("userId", sponsorUserId);

    return proxyUserIdQuery.combine(userIdQuery, CqlQuery::and);
  }
}
