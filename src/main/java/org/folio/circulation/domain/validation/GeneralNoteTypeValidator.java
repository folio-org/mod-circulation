package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.NoteType;
import org.folio.circulation.support.Result;

public class GeneralNoteTypeValidator {

  private GeneralNoteTypeValidator() {}

  public static Result<MultipleRecords<NoteType>> refuseIfNoteTypeNotFound(
    Result<MultipleRecords<NoteType>> noteTypeResult) {

    return noteTypeResult.failWhen(
      notes -> Result.succeeded(notes.getRecords().isEmpty()),
      notes -> singleValidationError("No General note type found", "noteTypes", null));
  }
}
