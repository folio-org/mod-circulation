package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import lombok.Value;

@Value
public class Departments {

  JsonObject representation;
  public Departments(JsonObject representation){
    System.out.println("Department values "+representation);
    this.representation = representation;
  }

  public String getDepartmentName(){
    return representation.getString("name");
  }

}
