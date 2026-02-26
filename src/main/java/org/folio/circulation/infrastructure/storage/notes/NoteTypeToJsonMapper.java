package org.folio.circulation.infrastructure.storage.notes;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.notes.NoteType;

import io.vertx.core.json.JsonObject;

public class NoteTypeToJsonMapper {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public NoteType fromJson(JsonObject json) {
    log.debug("fromJson:: converting JSON to note type");
    String id = getProperty(json, "id");
    NoteType noteType = new NoteType(id);
    log.info("fromJson:: result: note type created with id: {}", id);
    return noteType;
  }
}
