package org.folio.circulation.domain.notes;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.NoteLink;
import org.folio.circulation.infrastructure.storage.notes.NotesRepository;
import org.folio.circulation.support.results.Result;

public class NoteCreator {
  private final NotesRepository notesRepository;

  public NoteCreator(NotesRepository notesRepository) {
    this.notesRepository = notesRepository;
  }

  public CompletableFuture<Result<Note>> createGeneralUserNote(String userId, String message) {
    final GeneralNoteTypeValidator validator = new GeneralNoteTypeValidator();

    return notesRepository.findGeneralNoteType()
      .thenApply(validator::refuseIfNoteTypeNotFound)
      .thenCompose(r -> r.after(noteType -> notesRepository.create(Note.builder()
        .title(message)
        .typeId(noteType.getId())
        .content(message)
        // The domain must be users otherwise it won't be found by the reference UI
        // which uses a query like /note-links/domain/users/type/user/id/[userId]
        .domain("users")
        .link(new NoteLink(userId, NoteLinkType.USER.getValue()))
        .build())));
  }
}
