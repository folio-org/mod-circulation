package org.folio.circulation.domain.notes;

import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.util.Optional;

import org.folio.circulation.support.Result;

public final class GeneralNoteTypeValidator {
  public Result<NoteType> refuseIfNoteTypeNotFound(Result<Optional<NoteType>> result) {
    return result.failWhen(
        note -> Result.of(() -> !note.isPresent()),
        note -> singleValidationError("No General note type found", "noteTypes", null))
      .map(Optional::get);
  }
}
