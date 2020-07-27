package org.folio.circulation.domain;

import java.util.Arrays;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class NoteRepresentation extends JsonObject {
  public NoteRepresentation(NoteRepresentationBuilder builder) {
    super();

    this.put("title", builder.title);
    this.put("typeId", builder.typeId);
    this.put("content", builder.content);
    this.put("domain", builder.domain);
    this.put("links", builder.links);

  }

  public static NoteRepresentationBuilder builder() {
    return new NoteRepresentationBuilder();
  }

  public static class NoteRepresentationBuilder {
    String title;
    String typeId;
    String domain;
    String content;
    JsonArray links;

    public NoteRepresentationBuilder withTitle(String title) {
      this.title = title;
      return this;
    }

    public NoteRepresentationBuilder withTypeId(String typeId) {
      this.typeId = typeId;
      return this;
    }

    public NoteRepresentationBuilder withDomain(String domain) {
      this.domain = domain;
      return this;
    }

    public NoteRepresentationBuilder withContent(String content) {
      this.content = content;
      return this;
    }

    public NoteRepresentationBuilder withLinks(NoteLink ...links) {
      this.links = new JsonArray(Arrays.asList(links));
      return this;
    }

  }

}
