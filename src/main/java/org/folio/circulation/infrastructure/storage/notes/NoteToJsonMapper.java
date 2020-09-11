package org.folio.circulation.infrastructure.storage.notes;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.JsonPropertyWriter.write;

import java.util.List;
import java.util.stream.Collectors;

import org.folio.circulation.domain.NoteLink;
import org.folio.circulation.domain.notes.Note;
import org.folio.circulation.support.json.JsonObjectArrayPropertyFetcher;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class NoteToJsonMapper {
  public JsonObject toJson(Note note) {
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

    return json;
  }

  private static JsonObject toJson(NoteLink link) {
    JsonObject json = new JsonObject();

    write(json,"id", link.getId());
    write(json, "type", link.getType());

    return json;
  }

  public Note noteFrom(JsonObject json) {
    List<NoteLink> noteLinks = JsonObjectArrayPropertyFetcher.toStream(json, "links")
      .map(NoteToJsonMapper::noteLinkFrom)
      .collect(Collectors.toList());

    return new Note(getProperty(json,"id"), getProperty(json,"typeId"),
      getProperty(json,"domain"), getProperty(json,"title"),
      getProperty(json,"content"), noteLinks);
  }

  private static NoteLink noteLinkFrom(JsonObject json) {
    return new NoteLink(getProperty(json,"id"), getProperty(json,"type"));
  }
}
