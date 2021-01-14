package org.folio.circulation.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.folio.circulation.support.http.server.ValidationError;

import io.vertx.core.http.HttpServerResponse;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class MultiErrorFailure implements HttpFailure {

  private final Collection<HttpFailure> errors;

  @Override
  public void writeTo(HttpServerResponse response) {
    getErrorForResponse().writeTo(response);
  }

  private HttpFailure getErrorForResponse() {
    final List<ValidationError> validationErrors = new ArrayList<>();

    for (HttpFailure error : errors) {
      if (error instanceof ValidationErrorFailure) {
        validationErrors.addAll(((ValidationErrorFailure) error).getErrors());
      } else {
        return error;
      }
    }

    return new ValidationErrorFailure(validationErrors);
  }

  @Override
  public String toString() {
    return String.format("Multiple errors occurred:%n%s",
      errors.stream()
        .map(HttpFailure::toString)
        .collect(Collectors.joining(System.lineSeparator())));
  }
}
