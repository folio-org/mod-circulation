package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.util.Optional;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.notes.NoteType;
import org.folio.circulation.support.Result;

public final class GeneralNoteTypeValidator {
  private GeneralNoteTypeValidator() {}

  public static Result<NoteType> refuseIfNoteTypeNotFound(
    Result<Optional<NoteType>> noteTypeResult) {

    return noteTypeResult.failWhen(
        note -> Result.of(note::isEmpty),
        note -> singleValidationError("No General note type found", "noteTypes", null))
      .map(Optional::get);
  }
}
