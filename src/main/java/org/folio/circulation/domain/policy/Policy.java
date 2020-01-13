package org.folio.circulation.domain.policy;

public abstract class Policy {

  private String id;
  private String name;

  protected Policy(){

  }

  protected Policy(String id, String name) {
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
