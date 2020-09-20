package org.folio.circulation.domain;

public class UpdatedRequestPair {
  final Request original;
  final Request updated;

  public UpdatedRequestPair(Request original, Request updated) {
    this.original = original;
    this.updated = updated;
  }

  public Request getOriginal() {
    return original;
  }

  public Request getUpdated() {
    return updated;
  }
}
