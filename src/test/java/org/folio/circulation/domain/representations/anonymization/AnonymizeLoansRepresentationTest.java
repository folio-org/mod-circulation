package org.folio.circulation.domain.representations.anonymization;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnonymizeLoansRepresentationTest {

  @Test
  void mapToErrors() {
    Map<String, Collection<String>> multiMap = Map.of(
        "foo", List.of("foo1", "foo2"),
        "bar", List.of("bar1"));
    List<Error> errors = AnonymizeLoansRepresentation.mapToErrors(multiMap);
    assertThat(errors.size(), is(2));
    Error foo = errors.get(0);
    Error bar = errors.get(1);
    // accept in any order
    if ("foo".equals(bar.getMessage())) {
      foo = errors.get(1);
      bar = errors.get(0);
    }
    assertThat(foo.getMessage(), is("foo"));
    var parameters = foo.getParameters();
    assertThat(parameters.get(0).getKey(), is("loanIds"));
    assertThat(parameters.get(0).getValue(), is("[\"foo1\",\"foo2\"]"));

    assertThat(bar.getMessage(), is("bar"));
    parameters = bar.getParameters();
    assertThat(parameters.get(0).getKey(), is("loanIds"));
    assertThat(parameters.get(0).getValue(), is("[\"bar1\"]"));
  }

}
