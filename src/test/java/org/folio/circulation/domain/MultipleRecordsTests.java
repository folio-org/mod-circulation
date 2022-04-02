package org.folio.circulation.domain;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class MultipleRecordsTests {
  @Test
  void canCombineSinglePairOfRecords() {
    final var primaryId = UUID.randomUUID().toString();
    final var referenceId = UUID.randomUUID().toString();

    final var primary = new Primary(primaryId, referenceId, null);
    final var reference = new Reference(referenceId);

    final var primaryRecords = new MultipleRecords<>(List.of(primary), 1);
    final var referencedRecords = new MultipleRecords<>(List.of(reference), 1);

    // Combine the sets of records together and return a set of the primary records
    final var combinedRecords = primaryRecords.combineRecords(referencedRecords,
      MultipleRecords.CombinationMatchers.matchRecordsById(Primary::referenceId, Reference::id),
      Primary::withReferenced, null);

    assertThat("Should have one record", combinedRecords.size(), is(1));

    final var combinedPrimary = combinedRecords.firstOrNull();

    assertThat("Should have a primary record", combinedPrimary, is(notNullValue()));
    assertThat(combinedPrimary.referenced(), sameInstance(reference));
  }

  @Test
  void canCombineMultiplePairsOfRecords() {
    final var firstPrimaryId = UUID.randomUUID().toString();
    final var secondPrimaryId = UUID.randomUUID().toString();

    final var firstReferenceId = UUID.randomUUID().toString();
    final var secondReferenceId = UUID.randomUUID().toString();

    final var firstPrimary = new Primary(firstPrimaryId, secondReferenceId, null);
    final var secondPrimary = new Primary(secondPrimaryId, firstReferenceId, null);
    final var firstReference = new Reference(firstReferenceId);
    final var secondReference = new Reference(secondReferenceId);

    final var primaryRecords = new MultipleRecords<>(List.of(firstPrimary, secondPrimary), 2);
    final var referencedRecords = new MultipleRecords<>(List.of(firstReference, secondReference), 2);

    // Combine the sets of records together and return a set of the primary records
    final var combinedRecords = primaryRecords.combineRecords(referencedRecords,
      MultipleRecords.CombinationMatchers.matchRecordsById(Primary::referenceId, Reference::id),
      Primary::withReferenced, null);

    final var combinedFirstPrimary = combinedRecords
      .filter(p -> Objects.equals(p.id, firstPrimaryId)).firstOrNull();

    assertThat("Should have two records", combinedRecords.size(), is(2));

    assertThat("Should have a first primary record",
      combinedFirstPrimary, is(notNullValue()));

    assertThat(combinedFirstPrimary.referenced(), sameInstance(secondReference));

    final var combinedSecondPrimary = combinedRecords
      .filter(p -> Objects.equals(p.id, secondPrimaryId)).firstOrNull();

    assertThat("Should have a second primary record",
      combinedSecondPrimary, is(notNullValue()));

    assertThat(combinedSecondPrimary.referenced(), sameInstance(firstReference));
  }

  @Test
  void canDefaultReferenceRecordWhenCombining() {
    final var primaryId = UUID.randomUUID().toString();
    final var referenceId = UUID.randomUUID().toString();

    final var primary = new Primary(primaryId, referenceId, null);

    final var primaryRecords = new MultipleRecords<>(List.of(primary), 1);
    final var referencedRecords = new MultipleRecords<Reference>(List.of(), 0);

    final var defaultReference = new Reference(UUID.randomUUID().toString());

    // Combine the sets of records together and return a set of the primary records
    final var combinedRecords = primaryRecords.combineRecords(referencedRecords,
      MultipleRecords.CombinationMatchers.matchRecordsById(Primary::referenceId, Reference::id),
      Primary::withReferenced, defaultReference);

    assertThat("Should have one record", combinedRecords.size(), is(1));

    final var combinedPrimary = combinedRecords.firstOrNull();

    assertThat("Should have a primary record", combinedPrimary, is(notNullValue()));
    assertThat("Should be default record",
      combinedPrimary.referenced(), sameInstance(defaultReference));
  }

  static class Primary {
    private final String id;
    private final String referenceId;
    private final Reference referenced;

    Primary(String id, String referenceId, Reference referenced) {
      this.id = id;
      this.referenceId = referenceId;
      this.referenced = referenced;
    }

    public String id() {
      return id;
    }

    public String referenceId() {
      return referenceId;
    }

    public Primary withReferenced(Reference referenced) {
      return new Primary(id, referenceId, referenced
      );
    }

    public Reference referenced() {
      return referenced;
    }

    @Override
    public String toString() {
      return "Primary{" +
        "id='" + id + '\'' +
        ", referenceId='" + referenceId + '\'' +
        ", referenced=" + referenced +
        '}';
    }
  }

  static class Reference {
    private final String id;

    Reference(String id) {
      this.id = id;
    }

    public String id() {
      return id;
    }

    @Override
    public String toString() {
      return "Reference{" +
        "id='" + id + '\'' +
        '}';
    }
  }
}
