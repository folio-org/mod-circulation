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
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DepartmentRepository {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private final CollectionResourceClient departmentClient;

  public DepartmentRepository(Clients clients) {
    this.departmentClient = clients.departmentClient();
  }

  public CompletableFuture<Result<List<Department>>> getDepartmentByIds(List<String> departmentIds){
    log.info("getDepartmentByIds:: Fetching departmentByIds {}", departmentIds);
    return CqlQuery.exactMatchAny("id", departmentIds)
      .after(query -> departmentClient.getMany(query, PageLimit.noLimit()))
      .thenApply(result -> result.next(this::mapResponseToDepartments)
        .map(records -> new ArrayList<>(records.getRecords())));
  }

  private Result<MultipleRecords<Department>> mapResponseToDepartments(Response response) {
    return MultipleRecords.from(response, Department::new, "departments");
  }
}
