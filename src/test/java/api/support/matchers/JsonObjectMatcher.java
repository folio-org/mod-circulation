package api.support.matchers;

import static org.hamcrest.CoreMatchers.is;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.hamcrest.CoreMatchers;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import io.vertx.core.json.JsonObject;

public class JsonObjectMatcher extends TypeSafeDiagnosingMatcher<JsonObject> {

  public static <T> Matcher<JsonObject> hasJsonPath(String jsonPath, Matcher<T> valueMatcher) {
    return new JsonObjectMatcher(Collections.singletonList(
      com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath(jsonPath, valueMatcher)));
  }

  public static <T> Matcher<JsonObject> hasJsonPath(String jsonPath, T expectedValue) {
    return hasJsonPath(jsonPath, is(expectedValue));
  }

  @SafeVarargs
  public static Matcher<JsonObject> allOfPaths(Matcher<? super String>... jsonPathMatchers) {
    return new JsonObjectMatcher(Arrays.asList(jsonPathMatchers));
  }

  public static Matcher<JsonObject> allOfPaths(List<Matcher<? super String>> jsonPathMatchers) {
    return new JsonObjectMatcher(jsonPathMatchers);
  }

  public static Matcher<JsonObject> allOfPaths(Map<String, Matcher<String>> jsonPathMatchers) {
    return JsonObjectMatcher.allOfPaths(toStringMatcher(jsonPathMatchers));
  }

  public static Matcher<? super String> toStringMatcher(Map<String, Matcher<String>> jsonPathMatchers) {
    List<Matcher<? super String>> matchers = jsonPathMatchers.entrySet().stream()
      .map(e -> com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath(e.getKey(), e.getValue()))
      .collect(Collectors.toList());
    return CoreMatchers.allOf(matchers);
  }

  private final List<Matcher<? super String>> jsonPathMatchers;

  public JsonObjectMatcher(List<Matcher<? super String>> jsonPathMatchers) {
    this.jsonPathMatchers = jsonPathMatchers;
  }

  @Override
  protected boolean matchesSafely(JsonObject jsonObject, Description description) {
    String jsonString = jsonObject.encode();

    List<Matcher<? super String>> notMatched = jsonPathMatchers.stream()
      .filter(m -> !m.matches(jsonString))
      .collect(Collectors.toList());

    if (!notMatched.isEmpty()) {
      description.appendText(" not matched paths: ")
        .appendList("<", ", ", ">", notMatched);
      return false;
    }

    return true;
  }

  @Override
  public void describeTo(Description description) {
    description.appendText("json object with: ")
      .appendList("<", ", ", ">", jsonPathMatchers);
  }
}
