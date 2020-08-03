package org.folio.circulation.domain;

import java.util.Arrays;
import java.util.List;

public class NoteBuilder {
  String typeId;
  String domain;
  String title;
  String content;
  List<NoteLink> links;

  public Note build() {
    return new Note(null, typeId, domain, title, content, links);
  }

  public NoteBuilder withTitle(String title) {
    this.title = title;
    return this;
  }

  public NoteBuilder withTypeId(String typeId) {
    this.typeId = typeId;
    return this;
  }

  public NoteBuilder withDomain(String domain) {
    this.domain = domain;
    return this;
  }

  public NoteBuilder withContent(String content) {
    this.content = content;
    return this;
  }

  public NoteBuilder withLinks(NoteLink ...links) {
    this.links = Arrays.asList(links);
    return this;
  }
}
