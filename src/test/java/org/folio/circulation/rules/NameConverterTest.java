package org.folio.circulation.rules;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.junit.Test;

public class NameConverterTest {
  private Map<String,Map<String,String>> name2uuid = new HashMap<>();
  private Map<String,Map<String,String>> uuid2name = new HashMap<>();
  {
    Map<String,String> m = new HashMap<>();
    m.put("book", "123");
    m.put("dvd", "987");
    name2uuid.put("m", m);
    Map<String,String> policy = new HashMap<>();
    policy.put("loan-fallback", "0");
    policy.put("request-fallback", "1");
    policy.put("notice-fallback", "2");
    policy.put("policy-x", "3");
    policy.put("policy-y", "4");
    policy.put("policy-z", "5");
    name2uuid.put("policy", policy);

    for (Entry<String,Map<String,String>> typeMap : name2uuid.entrySet()) {
      String type = typeMap.getKey();
      Map<String,String> map = typeMap.getValue();
      Map<String,String> inverted = new HashMap<>();
      for (Entry<String,String> entry : map.entrySet()) {
        inverted.put(entry.getValue(), entry.getKey());
      }
      uuid2name.put(type, inverted);
    }
  }

  private String circulationRulesNames = String.join("\n",
      "priority: last-line",
      "fallback-policy: l loan-fallback r request-fallback n notice-fallback",
      "m book withoutreplacement dvd + t withoutreplacement: l policy-x r policy-y n policy-z",
      ""
  );
  private String circulationRulesUuids = String.join("\n",
      "priority: last-line",
      "fallback-policy: l 0 r 1 n 2",
      "m 123 withoutreplacement 987 + t withoutreplacement: l 3 r 4 n 5",
      ""
  );

  private String convertNames(String circulationRules, Map<String,Map<String,String>> replacements) {
    CharStream inputStream = CharStreams.fromString(circulationRules);
    CirculationRulesLexer lexer = new CirculationRulesLexer(inputStream);
    CommonTokenStream tokenStream = new CommonTokenStream(lexer);
    CirculationRulesParser parser = new CirculationRulesParser(tokenStream);
    parser.removeErrorListeners(); // remove ConsoleErrorListener
    parser.addErrorListener(new ErrorListener());
    ParseTree parseTree = parser.circulationRulesFile();
    ParseTreeWalker walker = new ParseTreeWalker();
    NameConverter nameConverter = new NameConverter(tokenStream, replacements);
    walker.walk(nameConverter, parseTree);
    return nameConverter.getText();
  }

  @Test
  public void replaceNames() {
    assertThat(convertNames(circulationRulesNames, name2uuid), is(circulationRulesUuids));
  }

  @Test
  public void replaceUuids() {
    assertThat(convertNames(circulationRulesUuids, uuid2name), is(circulationRulesNames));
  }
}
