package org.folio.circulation.domain.notes;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Note;
import org.folio.circulation.domain.NoteLink;
import org.folio.circulation.domain.NoteLinkType;
import org.folio.circulation.domain.validation.GeneralNoteTypeValidator;
import org.folio.circulation.infrastructure.storage.notes.NoteTypesRepository;
import org.folio.circulation.infrastructure.storage.notes.NotesRepository;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.utils.CollectionUtil;

public class NoteCreator {
  private final NotesRepository notesRepository;
  private final NoteTypesRepository noteTypesRepository;

  public NoteCreator(NotesRepository notesRepository, NoteTypesRepository noteTypesRepository) {
    this.notesRepository = notesRepository;
    this.noteTypesRepository = noteTypesRepository;
  }

  public CompletableFuture<Result<Note>> createNote(String userId, String message) {
    return noteTypesRepository.findByName("General note")
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
