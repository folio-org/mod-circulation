package org.folio.circulation.infrastructure.storage.notes;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import org.folio.circulation.domain.notes.NoteType;

import io.vertx.core.json.JsonObject;

public class NoteTypeToJsonMapper {
  public NoteType fromJson(JsonObject json) {
    return new NoteType(getProperty(json, "id"));
  }
}
