package org.folio.circulation.infrastructure.storage.users;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Department;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.User;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FindWithMultipleCqlIndexValues;
import org.folio.circulation.support.results.Result;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.fetching.RecordFetching.findWithMultipleCqlIndexValues;
import static org.folio.circulation.support.results.Result.succeeded;

public class DepartmentRepository {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private final CollectionResourceClient departmentClient;

  public DepartmentRepository(Clients clients) {
    this.departmentClient = clients.departmentClient();
  }

  public CompletableFuture<Result<User>> findDepartmentsForUser(Result<User> user) {
    if(user == null || user.value() == null){
      return completedFuture(succeeded(null));
    }
    return user.combineAfter(this::findDepartments, User::withDepartments);
  }

  public CompletableFuture<Result<MultipleRecords<Request>>> findDepartmentsForRequestUsers(
    MultipleRecords<Request> multipleRequests) {
    log.info("findDepartmentsForRequestUsers:: multipleRequests size {} ", multipleRequests.getTotalRecords());
    List<String> departmentIds = multipleRequests.getRecords().stream()
      .filter(req -> req.getRequester() != null)
      .map(req -> convertJsonArrayToList(req.getRequester()))
      .flatMap(List::stream)
      .distinct()
      .collect(Collectors.toList());
    return createDepartmentsFetcher()
      .findByIds(departmentIds)
      .thenApply(r -> r.next(dep -> matchDepartmentsToRequest(dep, multipleRequests)));
  }

  private Result<MultipleRecords<Request>> matchDepartmentsToRequest(
    MultipleRecords<Department> departments, MultipleRecords<Request> requests) {
    log.info("matchDepartmentsToRequest:: departments {} ", departments);
    Map<String, Department> departmentsMap = departments.toMap(Department::getId);
    return Result.succeeded(requests.mapRecords(req -> req.withRequester(
      req.getRequester().withDepartments(convertJsonArrayToList(req.getRequester())
        .stream()
        .map(departmentsMap::get)
        .filter(Objects::nonNull)
        .collect(Collectors.toList())))));
  }

  private List<String> convertJsonArrayToList(User user){
    return user.getDepartmentIds()
      .stream()
      .map(String.class::cast)
      .collect(Collectors.toList());
  }

  private CompletableFuture<Result<List<Department>>> findDepartments(User user) {
    log.debug("findDepartments:: Fetching departments for user Id {} , department Ids {}", user.getId(), user.getDepartmentIds());
    return createDepartmentsFetcher()
      .findByIds(convertJsonArrayToList(user))
      .thenApply(r -> r.map(rec -> new ArrayList<>(rec.getRecords())));
  }

  private FindWithMultipleCqlIndexValues<Department> createDepartmentsFetcher() {
    return findWithMultipleCqlIndexValues(departmentClient,
      "departments", Department::new);
  }

}
