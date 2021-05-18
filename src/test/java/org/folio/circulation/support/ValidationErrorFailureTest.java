package org.folio.circulation.support;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import org.junit.jupiter.api.Test;

class ValidationErrorFailureTest {

  @Test
  void singleValidationErrorOneParam() {
    var f = ValidationErrorFailure.singleValidationError("my.code", "foo {a} bar", "a", "b");
    assertThat(f.getErrors().size(), is(1));
    var e = f.getErrors().iterator().next();
    assertThat(e.getCode(), is("my.code"));
    assertThat(e.getMessage(), is("foo b bar"));
    assertThat(e.getParameter("a"), is("b"));
  }

  @Test
  void singleValidationErrorThreeParams() {
    var f = ValidationErrorFailure.singleValidationError("my.code", "foo {a}{b}{c}{a}{b}{c} bar",
        "a", "b", "b", "c", "c", "a");
    assertThat(f.getErrors().size(), is(1));
    var e = f.getErrors().iterator().next();
    assertThat(e.getCode(), is("my.code"));
    assertThat(e.getMessage(), is("foo bcabca bar"));
    assertThat(e.getParameter("a"), is("b"));
  }

}
