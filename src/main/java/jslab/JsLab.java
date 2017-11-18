package jslab;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.SimpleBindings;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class JsLab {
  public static void main(String[] args) {
    List<Integer> ints = Arrays.asList(7, 19, 3, 33, 14, 2, 9);
    ArrayList<Integer> sortedInts = new ArrayList<>(ints);
    sortedInts.sort((l, r) -> l < r ? 1 : l > r ? -1 : 0);
    System.out.println(sortedInts);
  }

  public static void xmain(String[] args) throws Exception {
    List<ScriptEngineFactory> factories = new ScriptEngineManager().getEngineFactories();
    for (ScriptEngineFactory factory : factories) {
      System.out.println(factory.getEngineName() + ": " + factory.getLanguageName() + ": " + factory.getExtensions());
    }

    //SimpleScriptContext scriptContext = new SimpleScriptContext();
    //scriptContext.setAttribute("x", "xx!", SimpleScriptContext.ENGINE_SCOPE);
    SimpleBindings scriptContext = new SimpleBindings();
    scriptContext.put("x", "alock");

    ScriptEngine engine = new ScriptEngineManager().getEngineByExtension("js");
    engine.eval(new FileReader("/data/projects/s3lab/src/main/resources/ng3/../jslab/script.js"), scriptContext);

    //Invocable invocable = (Invocable) engine;
    //invocable.invokeFunction("x");
  }
}
