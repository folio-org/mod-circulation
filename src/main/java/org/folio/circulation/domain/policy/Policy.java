package org.folio.circulation.domain.policy;

public abstract class Policy {

  private final String id;
  private final String name;

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
