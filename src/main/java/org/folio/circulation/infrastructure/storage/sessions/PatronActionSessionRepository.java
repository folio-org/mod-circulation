package org.folio.circulation.infrastructure.storage.sessions;

import static java.util.Collections.emptyList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toSet;
import static org.folio.circulation.domain.notice.session.PatronActionSessionProperties.ACTION_TYPE;
import static org.folio.circulation.domain.notice.session.PatronActionSessionProperties.ID;
import static org.folio.circulation.domain.notice.session.PatronActionSessionProperties.LOAN_ID;
import static org.folio.circulation.domain.notice.session.PatronActionSessionProperties.PATRON_ACTION_SESSIONS;
import static org.folio.circulation.domain.notice.session.PatronActionSessionProperties.PATRON_ID;
import static org.folio.circulation.domain.notice.session.PatronActionSessionProperties.SESSION_ID;
import static org.folio.circulation.support.AsyncCoordinationUtil.allOf;
import static org.folio.circulation.support.fetching.RecordFetching.findWithMultipleCqlIndexValues;
import static org.folio.circulation.support.http.ResponseMapping.flatMapUsingJson;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatchAny;
import static org.folio.circulation.support.http.client.CqlQuery.noQuery;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;
import static org.folio.circulation.support.results.CommonFailures.failedDueToServerError;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.of;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.flatMapResult;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Campus;
import org.folio.circulation.domain.Institution;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Location;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.notice.session.ExpiredSession;
import org.folio.circulation.domain.notice.session.PatronActionType;
import org.folio.circulation.domain.notice.session.PatronSessionRecord;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.infrastructure.storage.inventory.LocationRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanPolicyRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.ForwardOnFailure;
import org.folio.circulation.support.fetching.CqlQueryFinder;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.http.client.ResponseInterpreter;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;

public class PatronActionSessionRepository {

  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private final CollectionResourceClient patronActionSessionsStorageClient;
  private final LoanRepository loanRepository;
  private final LoanPolicyRepository loanPolicyRepository;
  private final UserRepository userRepository;
  private final LocationRepository locationRepository;

  public static PatronActionSessionRepository using(Clients clients,
    LoanRepository loanRepository, UserRepository userRepository) {

    return new PatronActionSessionRepository(
      clients.patronActionSessionsStorageClient(),
      loanRepository, userRepository,
      new LoanPolicyRepository(clients),
      LocationRepository.using(clients, new ServicePointRepository(clients)));
  }

  private PatronActionSessionRepository(
    CollectionResourceClient patronActionSessionsStorageClient,
    LoanRepository loanRepository,
    UserRepository userRepository,
    LoanPolicyRepository loanPolicyRepository,
    LocationRepository locationRepository) {

    this.patronActionSessionsStorageClient = patronActionSessionsStorageClient;
    this.loanRepository = loanRepository;
    this.userRepository = userRepository;
    this.loanPolicyRepository = loanPolicyRepository;
    this.locationRepository = locationRepository;
  }

  public CompletableFuture<Result<PatronSessionRecord>> create(PatronSessionRecord patronSessionRecord) {
    JsonObject representation = mapToJson(patronSessionRecord);

    final ResponseInterpreter<PatronSessionRecord> responseInterpreter
      = new ResponseInterpreter<PatronSessionRecord>()
      .flatMapOn(201, flatMapUsingJson(this::mapFromJson));

    return patronActionSessionsStorageClient.post(representation)
      .thenApply(responseInterpreter::flatMap);
  }

  public CompletableFuture<Result<PatronSessionRecord>> delete(PatronSessionRecord session) {
    var interpreter = new ResponseInterpreter<PatronSessionRecord>()
      .on(204, of(() -> session))
      .otherwise(response -> {
        log.info("Error has occurred while deleting PatronSessionRecord with id {} and action type {}",
          session.getId(), session.getActionType().name());
        return failed(new ForwardOnFailure(response));
      });

    log.info("Deleting {}", session);

    return patronActionSessionsStorageClient.delete(session.getId().toString())
      .thenApply(flatMapResult(interpreter::apply));
  }

  private JsonObject mapToJson(PatronSessionRecord patronSessionRecord) {
    JsonObject json = new JsonObject();
    write(json, ID, patronSessionRecord.getId());
    write(json, PATRON_ID, patronSessionRecord.getPatronId());
    write(json, LOAN_ID, patronSessionRecord.getLoanId());
    write(json, ACTION_TYPE, patronSessionRecord.getActionType().getRepresentation());
    write(json, SESSION_ID, patronSessionRecord.getSessionId());

    return json;
  }

  private Result<PatronSessionRecord> mapFromJson(JsonObject json) {

    PatronSessionRecord patronSessionRecord = PatronSessionRecord.from(json);
    if (patronSessionRecord == null) {
      return failedDueToServerError("Invalid patron action type value: "
        + getProperty(json, ACTION_TYPE));
    }
    return succeeded(patronSessionRecord);
  }

  public CompletableFuture<Result<List<PatronSessionRecord>>> findPatronActionSessions(
    String patronId, PatronActionType actionType, PageLimit pageLimit) {

    Result<CqlQuery> sessionsQuery = exactMatch(PATRON_ID, patronId);

    sessionsQuery = addActionTypeToCqlQuery(sessionsQuery, actionType);

    return sessionsQuery
      .after(query -> findBy(query, pageLimit))
      .thenCompose(r -> r.combineAfter(() -> userRepository.getUser(patronId), this::setUserForLoans))
      .thenApply(mapResult(this::toList));
  }

  public CompletableFuture<Result<Collection<PatronSessionRecord>>> findPatronActionSessions(
    Collection<String> sessionIds, PageLimit pageLimit) {

    log.debug("findPatronActionSessions:: sessionIds: {}", sessionIds);

    if (sessionIds.isEmpty()) {
      log.debug("findPatronActionSessions:: collection of session IDs is empty");
      return ofAsync(emptyList());
    }

    return new CqlQueryFinder<>(patronActionSessionsStorageClient, "patronActionSessions",
      PatronSessionRecord::from)
      .findByQuery(exactMatchAny(SESSION_ID, sessionIds), pageLimit)
      .thenApply(r -> r.map(MultipleRecords::getRecords));
  }

  public CompletableFuture<Result<List<PatronSessionRecord>>> findPatronActionSessions(
    List<ExpiredSession> expiredSessions) {

    Set<String> patronIds = expiredSessions.stream()
      .map(ExpiredSession::getPatronId)
      .filter(StringUtils::isNotBlank)
      .collect(toSet());

    if (patronIds.isEmpty()) {
      log.info("List of patron IDs is empty. Doing nothing.");
      return CompletableFuture.completedFuture(succeeded(null));
    }

    Result<CqlQuery> actionTypeQuery = createActionTypeCqlQuery(
      expiredSessions.get(0).getActionType());

    return findWithMultipleCqlIndexValues(patronActionSessionsStorageClient,
      PATRON_ACTION_SESSIONS, PatronSessionRecord::from)
      .findByIdIndexAndQuery(patronIds, PATRON_ID, actionTypeQuery)
      .thenCompose(r -> r.after(this::fetchLoans))
      .thenCompose(r -> r.combineAfter(
        () -> userRepository.getUsersForUserIds(patronIds), this::setUsersForLoans))
      .thenApply(mapResult(this::toList));
  }

  private <T> List<T> toList(MultipleRecords<T> records) {
    return new ArrayList<>(records.getRecords());
  }

  private Result<CqlQuery> addActionTypeToCqlQuery(
    Result<CqlQuery> sessionsQuery, PatronActionType actionType) {

    if (isPatronActionTypeSpecified(actionType)) {
      final Result<CqlQuery> actionTypeQuery = exactMatch(ACTION_TYPE,
        actionType.getRepresentation());
      sessionsQuery = sessionsQuery.combine(actionTypeQuery, CqlQuery::and);
    }
    return sessionsQuery;
  }

  private Result<CqlQuery> createActionTypeCqlQuery(PatronActionType actionType) {

    if (isPatronActionTypeSpecified(actionType)) {
      return exactMatch(ACTION_TYPE, actionType.getRepresentation());
    }
    return noQuery();
  }

  private boolean isPatronActionTypeSpecified(PatronActionType actionType) {
    return !PatronActionType.ALL.equals(actionType);
  }

  private MultipleRecords<PatronSessionRecord> setUserForLoans(
    MultipleRecords<PatronSessionRecord> records, User user) {

    return records.mapRecords(sessionRecord -> {
        if (sessionRecord.getLoan() != null) {
          return sessionRecord.withLoan(sessionRecord.getLoan().withUser(user));
        }
        log.info("Session with ID: {} doesn't have a loan", sessionRecord.getId());
        return sessionRecord;
      }
    );
  }

  private MultipleRecords<PatronSessionRecord> setUsersForLoans(
    MultipleRecords<PatronSessionRecord> records, Map<String, User> usersMap) {

    return records.mapRecords(sessionRecord -> {
      if (sessionRecord.getLoan() != null && sessionRecord.getPatronId() != null) {

        return sessionRecord.withLoan(sessionRecord.getLoan().withUser(
          usersMap.get(sessionRecord.getPatronId().toString())));
      }
      log.info("Loans were not fetched for the session records");
      return sessionRecord;
    });
  }

  private CompletableFuture<Result<MultipleRecords<PatronSessionRecord>>> findBy(
    CqlQuery query, PageLimit pageLimit) {

    return patronActionSessionsStorageClient.getMany(query, pageLimit)
      .thenApply(r -> r.next(response ->
        MultipleRecords.from(response, identity(), PATRON_ACTION_SESSIONS)))
      .thenApply(r -> r.next(records -> records.flatMapRecords(this::mapFromJson)))
      .thenCompose(r -> r.after(this::fetchLoans));
  }

  private CompletableFuture<Result<MultipleRecords<PatronSessionRecord>>> fetchLoans(
    MultipleRecords<PatronSessionRecord> sessionRecords) {

    List<String> loanIds = sessionRecords.getRecords().stream()
      .map(PatronSessionRecord::getLoanId)
      .map(UUID::toString)
      .collect(Collectors.toList());

    return loanRepository.findByIds(loanIds)
      .thenCompose(r -> r.after(this::fetchCampusesForLoanItems))
      .thenCompose(r -> r.after(this::fetchInstitutionsForLoanItems))
      .thenCompose(r -> r.after(this::fetchLatestPatronInfoAddedComment))
      .thenCompose(r -> r.after(loanPolicyRepository::findLoanPoliciesForLoans))
      .thenApply(mapResult(loans -> setLoansForSessionRecords(sessionRecords, loans)));
  }

  private CompletableFuture<Result<MultipleRecords<Loan>>> fetchCampusesForLoanItems(
    MultipleRecords<Loan> loans) {

    List<Location> locations = getLocations(loans);
    if (locations.isEmpty()) {
      return Result.ofAsync(() -> loans);
    }

    return locationRepository.getCampuses(locations)
      .thenApply(mapResult(campuses ->
        loans.mapRecords(loan -> setCampusForLoanItem(loan, campuses))));
  }

  private Loan setCampusForLoanItem(Loan loan, Map<String, Campus> campuses) {
    Item item = loan.getItem();

    if (item.isNotFound()) {
      return loan;
    }

    Location oldLocation = item.getLocation();

    final var campus = campuses.getOrDefault(oldLocation.getCampusId(),
      Campus.unknown(oldLocation.getCampusId()));
    Location locationWithCampus = oldLocation.withCampus(campus);

    return loan.withItem(item.withLocation(locationWithCampus));
  }

  private CompletableFuture<Result<MultipleRecords<Loan>>> fetchInstitutionsForLoanItems(
    MultipleRecords<Loan> loans) {

    List<Location> locations = getLocations(loans);
    if (locations.isEmpty()) {
      return Result.ofAsync(() -> loans);
    }

    return locationRepository.getInstitutions(locations)
      .thenApply(mapResult(institutions ->
        loans.mapRecords(loan -> setInstitutionForLoanItem(loan, institutions))));
  }

  private CompletableFuture<Result<MultipleRecords<Loan>>> fetchLatestPatronInfoAddedComment(
    MultipleRecords<Loan> loans) {
    return allOf(loans.getRecords(), loanRepository::fetchLatestPatronInfoAddedComment)
      .thenApply(r -> r.map(list -> new MultipleRecords<>(list, loans.getTotalRecords())));
  }

  private Loan setInstitutionForLoanItem(Loan loan, Map<String, Institution> institutions) {
    Item item = loan.getItem();

    if (item.isNotFound()) {
      return loan;
    }

    Location oldLocation = item.getLocation();

    final var institution = institutions.getOrDefault(oldLocation.getInstitutionId(),
      Institution.unknown(oldLocation.getInstitutionId()));
    Location locationWithInstitution = oldLocation.withInstitution(institution);

    return loan.withItem(item.withLocation(locationWithInstitution));
  }

  private static MultipleRecords<PatronSessionRecord> setLoansForSessionRecords(
    MultipleRecords<PatronSessionRecord> sessionRecords, MultipleRecords<Loan> loans) {

    Map<String, Loan> loanMap = loans.toMap(Loan::getId);

    return sessionRecords.mapRecords(r ->
      r.withLoan(loanMap.get(r.getLoanId().toString())));
  }

  private List<Location> getLocations(MultipleRecords<Loan> loans) {
    return loans.getRecords().stream()
      .map(Loan::getItem)
      .filter(Item::isFound)
      .map(Item::getLocation)
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }
}
