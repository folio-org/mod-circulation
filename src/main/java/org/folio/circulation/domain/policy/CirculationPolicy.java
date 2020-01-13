package org.folio.circulation.domain.policy;

public abstract class CirculationPolicy {

  private String id;
  private String name;

  protected CirculationPolicy(){

  }

  protected CirculationPolicy(String id, String name) {
    this.id = id;
    this.name = name;
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }
}
