package org.folio.circulation.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang3.builder.ToStringBuilder;
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
    return new ToStringBuilder(this)
      .append("errors", errors)
      .toString();
  }
}
