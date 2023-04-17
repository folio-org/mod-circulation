package org.folio.circulation.infrastructure.storage.users;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Department;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.results.Result;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class DepartmentRepository {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private final CollectionResourceClient departmentClient;
  public static final int BATCH_SIZE = 90;

  public DepartmentRepository(Clients clients) {
    this.departmentClient = clients.departmentClient();
  }

  public List<Department> getDepartmentByIds(List<String> departmentIds) {
    log.debug("getDepartmentByIds:: Fetching departmentByIds {}", departmentIds);
    return findDepartmentList(departmentIds).stream()
      .map(Result::value)
      .map(rec -> new ArrayList<>(rec.getRecords()))
      .flatMap(Collection::stream)
      .collect(Collectors.toList());
  }

  private List<Result<MultipleRecords<Department>>> findDepartmentList(List<String> departmentIds) {
    return splitIds(departmentIds)
      .stream()
      .map(dep -> findDepartmentByCql(CqlQuery.exactMatchAny("id", dep),
        departmentClient, PageLimit.noLimit()))
      .map(CompletableFuture::join)
      .collect(Collectors.toList());
  }

  private CompletableFuture<Result<MultipleRecords<Department>>> findDepartmentByCql(
    Result<CqlQuery> query, CollectionResourceClient client, PageLimit pageLimit) {
    return query.after(cqlQuery -> client.getMany(cqlQuery, pageLimit))
      .thenApply(resp -> resp.next(this::mapResponseToDepartments));
  }

  private Result<MultipleRecords<Department>> mapResponseToDepartments(Response response) {
    return MultipleRecords.from(response, Department::new, "departments");
  }

  private List<List<String>> splitIds(List<String> departmentIds) {
    int size = departmentIds.size();
    if (size == 0) {
      return new ArrayList<>();
    }
    int fullChunks = (size - 1) / BATCH_SIZE;
    return IntStream.range(0, fullChunks + 1)
      .mapToObj(n ->
        departmentIds.subList(n * BATCH_SIZE, n == fullChunks
          ? size
          : (n + 1) * BATCH_SIZE))
      .collect(Collectors.toList());
  }
}
