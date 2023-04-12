package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import lombok.Value;

@Value
public class Department {

  JsonObject representation;
  public Department(JsonObject representation){
    System.out.println("Department values "+representation);
    this.representation = representation;
  }

  public String getName(){
    return representation.getString("name");
  }

}
