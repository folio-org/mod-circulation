package org.folio.circulation.domain;

import static org.folio.circulation.support.JsonPropertyFetcher.getNestedStringProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

import org.joda.time.DateTime;

import com.google.inject.internal.util.Lists;

import io.vertx.core.json.JsonObject;

public class Account {

  private final JsonObject representation;
  private Collection<FeeFineAction> feeFineActions = Lists.newArrayList();

  public Account(JsonObject representation) {
    this.representation = representation;
  }

  private Account(JsonObject representation, Collection<FeeFineAction> actions) {
    this.representation = representation;
    this.feeFineActions = actions;
  }

  public static Account from(JsonObject representation) {
    return new Account(representation);
  }

  public String getId() {
    return getProperty(representation, "id");
  }

  public String getLoanId() {
    return getProperty(representation, "loanId");
  }

  public Double getRemainingFeeFineAmount() {
    return representation.getDouble("remaining");
  }

  public String getStatus() {
    return getNestedStringProperty(representation, "status", "name");
  }

  public Optional<DateTime> getClosedDate() {

    return feeFineActions.stream()
      .filter(ffa -> ffa.getBalance() == 0d)
      .max(Comparator.comparing(FeeFineAction::getDateAction))
      .map(FeeFineAction::getDateAction);
  }

  public Account withFeefineActions(Collection<FeeFineAction> actions) {
    return new Account(representation, actions);
  }

  public boolean isClosed() {
    return getStatus().equalsIgnoreCase("closed");
  }

  public boolean isOpen() {
    return getStatus().equalsIgnoreCase("open");
  }
}
