package org.folio.circulation.infrastructure.storage.loans;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAction;
import org.folio.circulation.domain.LoanHistory;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.fetching.GetManyRecordsRepository;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.Offset;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.results.Result;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;

import static org.folio.circulation.support.CqlSortBy.sortBy;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.CqlSortClause.descending;
import static org.folio.circulation.support.results.ResultBinding.flatMapResult;

public class LoanHistoryRepository implements GetManyRecordsRepository<LoanHistory> {
  private static final String RECORDS_PROPERTY_NAME = "loansHistory";
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private final CollectionResourceClient loansHistoryStorageClient;

  public LoanHistoryRepository(Clients clients) {
    loansHistoryStorageClient = clients.loansHistoryStorageClient();
  }

  public CompletableFuture<Result<MultipleRecords<LoanHistory>>> getLatestPatronInfoAdded(Loan loan) {
    return exactMatch("loan.id", loan.getId())
      .combine(exactMatch("loan.action", LoanAction.PATRON_INFO_ADDED.getValue()), CqlQuery::and)
      .map(q -> q.sortBy(sortBy(descending("createdDate"))))
      .after(q -> getMany(q, PageLimit.one(), Offset.zeroOffset()));
  }

  @Override
  public CompletableFuture<Result<MultipleRecords<LoanHistory>>> getMany(CqlQuery cqlQuery, PageLimit pageLimit, Offset offset) {
    log.debug("getMany:: parameters cqlQuery: {}, pageLimit: {}, offset: {}", cqlQuery, pageLimit, offset);
    return loansHistoryStorageClient.getMany(cqlQuery, pageLimit, offset)
      .thenApply(flatMapResult(this::mapResponseToLoans));
  }

  private Result<MultipleRecords<LoanHistory>> mapResponseToLoans(Response response) {
    log.debug("mapResponseToLoans:: parameters response: {}", response);
    return MultipleRecords.from(response, LoanHistory::from, RECORDS_PROPERTY_NAME);
  }
}
