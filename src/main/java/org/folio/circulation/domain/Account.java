package org.folio.circulation.domain;

import static org.folio.circulation.support.JsonPropertyFetcher.getNestedStringProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

import org.apache.commons.lang3.math.NumberUtils;
import org.joda.time.DateTime;

import io.vertx.core.json.JsonObject;

public class Account {

  private final JsonObject representation;
  private Collection<FeeFineAction> feeFineActions = new ArrayList<>();

  public Account(JsonObject representation) {
    this.representation = representation;
  }

  private Account(JsonObject representation, Collection<FeeFineAction> actions) {
    this.representation = representation;
    this.feeFineActions = actions == null ? new ArrayList<>() : actions;
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
      .filter(ffa -> ffa.getBalance().equals(NumberUtils.DOUBLE_ZERO))
      .max(Comparator.comparing(FeeFineAction::getDateAction))
      .map(FeeFineAction::getDateAction);
  }

  public Account withFeeFineActions(Collection<FeeFineAction> actions) {
    return new Account(representation, actions);
  }

  public boolean isClosed() {
    return getStatus().equalsIgnoreCase("closed");
  }

  public boolean isOpen() {
    return getStatus().equalsIgnoreCase("open");
  }
}
