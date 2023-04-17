package api.support.fixtures;

import api.support.http.IndividualResource;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;

import static org.folio.circulation.support.json.JsonPropertyWriter.write;

public class DepartmentFixture {

  private final ResourceClient departmentClient;

  public DepartmentFixture() {
    departmentClient = ResourceClient.forDepartmentStorage();
  }

  public IndividualResource department(String id) {

    final JsonObject department = new JsonObject();

    write(department, "id", id);
    write(department, "name", "test department type");

    return departmentClient.create(department);
  }

}
