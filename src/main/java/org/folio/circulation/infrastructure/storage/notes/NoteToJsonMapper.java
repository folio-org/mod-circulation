package org.folio.circulation.infrastructure.storage.notes;

import static org.folio.circulation.support.JsonPropertyFetcher.getArrayProperty;
import static org.folio.circulation.support.JsonPropertyWriter.write;

import java.util.List;
import java.util.stream.Collectors;

import org.folio.circulation.domain.NoteLink;
import org.folio.circulation.domain.notes.Note;
import org.folio.circulation.support.JsonArrayHelper;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class NoteToJsonMapper {
  private static NoteLink noteLinkFrom(JsonObject jsonObject) {
    return new NoteLink(jsonObject.getString("id"), jsonObject.getString("type"));
  }

  public static JsonObject toJson(Note note) {
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

  public static JsonObject toJson(NoteLink link) {
    JsonObject json = new JsonObject();

    write(json,"id", link.getId());
    write(json, "type", link.getType());

    return json;
  }

  public Note noteFrom(JsonObject jsonObject) {
    JsonArray noteLinksJson = getArrayProperty(jsonObject, "links");
    List<NoteLink> noteLinks = JsonArrayHelper.toStream(noteLinksJson)
      .map(NoteToJsonMapper::noteLinkFrom)
      .collect(Collectors.toList());

    return new Note(jsonObject.getString("id"), jsonObject.getString("typeId"),
      jsonObject.getString("domain"), jsonObject.getString("title"),
      jsonObject.getString("content"), noteLinks);
  }
}
