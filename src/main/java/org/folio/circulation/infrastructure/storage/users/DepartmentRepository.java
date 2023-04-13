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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class DepartmentRepository {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private final CollectionResourceClient departmentClient;
  public static final int BATCH_SIZE = 2;

  public DepartmentRepository(Clients clients) {
    this.departmentClient = clients.departmentClient();
  }

  public List<Department> getDepartmentByIds(List<String> departmentIds) {
    log.info("getDepartmentByIds:: Fetching departmentByIds {}", departmentIds);
    if (departmentIds.isEmpty()) {
      return Collections.emptyList();
    }
    List<Result<ArrayList<Department>>> id = splitIds(departmentIds)
      .stream()
      .map(dep -> CqlQuery.exactMatchAny("id", dep)
        .after(query -> {
          log.info("departmentClient.getMany");
          return departmentClient.getMany(query, PageLimit.noLimit());})
        .thenApply(result -> result.next(this::mapResponseToDepartments)
          .map(records -> new ArrayList<>(records.getRecords()))))
      .map(CompletableFuture::join)
      .collect(Collectors.toList());
    return id.stream()
      .map(Result::value)
      .flatMap(Collection::stream)
      .collect(Collectors.toList());

  }

  private Result<MultipleRecords<Department>> mapResponseToDepartments(Response response) {
    return MultipleRecords.from(response, Department::new, "departments");
  }

  private List<List<String>> splitIds(List<String> itemsIds) {
    int size = itemsIds.size();
    if (size == 0) {
      return new ArrayList<>();
    }
    int fullChunks = (size - 1) / BATCH_SIZE;
    return IntStream.range(0, fullChunks + 1)
      .mapToObj(n ->
        itemsIds.subList(n * BATCH_SIZE, n == fullChunks
          ? size
          : (n + 1) * BATCH_SIZE))
      .collect(Collectors.toList());
  }
}
