package org.folio.circulation.rules;

/**
 * Store the UUID of the policy.
 */
public class Policy {
  /** UUID of the loan policy. */
  @SuppressWarnings("squid:ClassVariableVisibilityCheck")  // Drools directly uses public fields
  public String id;

  /**
   * Set policy.
   * @param id  UUID of the policy.
   */
  public Policy(String id) {
    this.id = id;
  }

  @Override
  public String toString() {
    return id;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Policy)) {
      return false;
    }
    Policy other = (Policy) o;
    if (id == null) {
      return other.id == null;
    }
    return id.equals(other.id);
  }

  @Override
  public int hashCode() {
    if (id == null) {
      return 0;
    }
    return id.hashCode();
  }
}
