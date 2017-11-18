package jslab;

import javax.script.*;
import java.io.FileReader;
import java.util.List;

public class JsLab {
  public static void main(String[] args) throws Exception {
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
