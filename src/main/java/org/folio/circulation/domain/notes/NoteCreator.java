package org.folio.circulation.domain.notes;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.NoteLink;
import org.folio.circulation.domain.validation.GeneralNoteTypeValidator;
import org.folio.circulation.infrastructure.storage.notes.NotesRepository;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.utils.CollectionUtil;

public class NoteCreator {
  private final NotesRepository notesRepository;

  public NoteCreator(NotesRepository notesRepository) {
    this.notesRepository = notesRepository;
  }

  public CompletableFuture<Result<Note>> createNote(String userId, String message) {
    return notesRepository.findGeneralNoteType()
      .thenApply(GeneralNoteTypeValidator::refuseIfNoteTypeNotFound)
      .thenApply(r -> r.map(CollectionUtil::firstOrNull))
      .thenCompose(r -> r.after(noteType -> notesRepository.create(Note.builder()
        .title(message)
        .typeId(noteType.getId())
        .content(message)
        .domain("loans")
        .link(NoteLink.from(userId, NoteLinkType.USER.getValue()))
        .build())));
  }

}
