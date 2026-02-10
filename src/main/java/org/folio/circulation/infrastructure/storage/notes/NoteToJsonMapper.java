package org.folio.circulation.infrastructure.storage.notes;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.NoteLink;
import org.folio.circulation.domain.notes.Note;
import org.folio.circulation.support.json.JsonObjectArrayPropertyFetcher;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class NoteToJsonMapper {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public JsonObject toJson(Note note) {
    log.debug("toJson:: converting note to JSON");
    JsonObject json = new JsonObject();

    List<JsonObject> jsonLinks = note.getLinks().stream()
      .map(NoteToJsonMapper::toJson)
      .collect(Collectors.toList());

    write(json,"id", note.getId());
    write(json, "typeId", note.getTypeId());
    write(json, "domain", note.getDomain());
    write(json, "title", note.getTitle());
    write(json, "content", note.getContent());
    write(json, "links", new JsonArray(jsonLinks));

    log.info("toJson:: result: note converted to JSON successfully with {} links", jsonLinks.size());
    return json;
  }

  private static JsonObject toJson(NoteLink link) {
    log.debug("toJson:: converting note link to JSON");
    JsonObject json = new JsonObject();

    write(json,"id", link.getId());
    write(json, "type", link.getType());

    return json;
  }

  public Note noteFrom(JsonObject json) {
    log.debug("noteFrom:: converting JSON to note");
    List<NoteLink> noteLinks = JsonObjectArrayPropertyFetcher.toStream(json, "links")
      .map(NoteToJsonMapper::noteLinkFrom)
      .collect(Collectors.toList());

    Note note = new Note(getProperty(json,"id"), getProperty(json,"typeId"),
      getProperty(json,"domain"), getProperty(json,"title"),
      getProperty(json,"content"), noteLinks);

    log.info("noteFrom:: result: note created from JSON with {} links", noteLinks.size());
    return note;
  }

  private static NoteLink noteLinkFrom(JsonObject json) {
    log.debug("noteLinkFrom:: converting JSON to note link");
    return new NoteLink(getProperty(json,"id"), getProperty(json,"type"));
  }
}
