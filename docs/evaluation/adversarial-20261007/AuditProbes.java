import com.buildai.app.runtime.*;
import com.buildai.app.builder.*;
import org.json.*;
import java.util.*;
import java.nio.file.*;

/** Characterization probes: PASS means a defect was reproduced, not fixed. */
public class AuditProbes {
    static int count;
    static void reproduced(String name, boolean condition) {
        if (!condition) throw new AssertionError("Could not reproduce: " + name);
        count++;
        System.out.println("REPRODUCED " + name);
    }
    static Expr literal(String n) { return new Expr(ExpressionOp.NUMBER, n, List.of()); }
    static Expr input(String id) { return new Expr(ExpressionOp.INPUT, id, List.of()); }
    static Expr binary(ExpressionOp op, Expr a, Expr b) { return new Expr(op, "", List.of(a, b)); }
    static ExpressionContext ctx(Map<String,String> inputs) {
        return new ExpressionContext(inputs, Map.of(), Map.of(), 0L);
    }
    static JSONObject plain() {
        return new JSONObject("""
            {"schemaVersion":1,"name":"Audit","startScreen":"home","screens":[
              {"id":"home","title":"Home","components":[{"id":"text","kind":"TEXT","label":"Hello"}]}],
             "entities":[],"actions":[]}
            """);
    }
    public static void main(String[] args) throws Exception {
        AppSpec original = AppSpec.Companion.parse("""
            {"schemaVersion":1,"name":"Shared action","startScreen":"home","screens":[
             {"id":"home","title":"Home","components":[{"id":"first","kind":"BUTTON","label":"Go","actionId":"shared"}]},
             {"id":"other","title":"Other","components":[{"id":"second","kind":"BUTTON","label":"Go","actionId":"shared"}]}],
             "entities":[],"actions":[{"id":"shared","kind":"NAVIGATE","target":"other","label":"Go","values":{}}]}
            """);
        EditScope scope = new EditScope("home", Set.of("first"));
        JSONObject response = new JSONObject("""
            {"actions":[{"id":"shared","kind":"NAVIGATE","target":"home","label":"Changed","values":{}}]}
            """);
        AppSpec edited = ScopedEditTools.INSTANCE.prepare(original, scope, response);
        reproduced("A01 shared action changes unselected screen behavior",
            edited.getScreens().get(1).equals(original.getScreens().get(1)) &&
            !edited.getActions().equals(original.getActions()));
        AppSpec unchanged = ScopedEditTools.INSTANCE.prepare(original, scope, new JSONObject());
        reproduced("A02 empty scoped edit is accepted", unchanged.equals(original));

        AppSpec notes = ManualExamples.INSTANCE.notes();
        JSONObject newField = new JSONObject("""
            {"newFields":[{"entity":"notes","field":{"id":"category","label":"Category","kind":"TEXT","required":false}}]}
            """);
        AppSpec schemaEdit = ScopedEditTools.INSTANCE.prepare(notes, new EditScope("home", Set.of("notes")), newField);
        reproduced("A03 field-only change has an empty review change list",
            !schemaEdit.getEntities().equals(notes.getEntities()) && ScopedEditTools.INSTANCE.changedIds(notes, schemaEdit).isEmpty());

        AppSpec constantCalculator = AppSpec.Companion.parse("""
            {"schemaVersion":1,"name":"Calculator","startScreen":"home","screens":[
              {"id":"home","title":"Home","components":[
               {"id":"a","kind":"INPUT","label":"A","fieldKind":"NUMBER"},
               {"id":"b","kind":"INPUT","label":"B","fieldKind":"NUMBER"},
               {"id":"total","kind":"SUMMARY","label":"Total","expression":
                {"op":"ADD","args":[{"op":"NUMBER","value":"2"},{"op":"NUMBER","value":"3"}]}}]}],
             "entities":[],"actions":[]}
            """);
        CompositionSelection selection = CompositionSelection.Companion.read(new JSONObject("""
            {"components":["INPUT","SUMMARY"],"expressions":["INPUT","ADD"],"actions":[],"records":false,"screens":false}
            """));
        JSONObject fragment = new JSONObject().put("components", constantCalculator.json().getJSONArray("screens").getJSONObject(0).getJSONArray("components"))
            .put("entities", new JSONArray()).put("screens", new JSONArray()).put("actions", new JSONArray());
        selection.validateImplementation(constantCalculator, fragment);
        Object output = Expressions.INSTANCE.evaluate(constantCalculator.getScreens().get(0).getComponents().get(2).getExpression(), ctx(Map.of("a","10","b","20")), 0);
        reproduced("A04 live calculator contract accepts constant-only output", Expressions.INSTANCE.display(output).equals("5"));

        Expr nested = binary(ExpressionOp.MULTIPLY, binary(ExpressionOp.DIVIDE, literal("1"), literal("3")), literal("3"));
        boolean precisionFailure = false;
        try { Expressions.INSTANCE.evaluate(nested, ctx(Map.of()), 0); }
        catch (IllegalArgumentException error) { precisionFailure = error.getMessage().contains("precision"); }
        reproduced("A05 nested division fails supported-number precision", precisionFailure);

        Object equal = Expressions.INSTANCE.evaluate(binary(ExpressionOp.EQ, input("amount"), literal("2.5")), ctx(Map.of("amount","2,50")), 0);
        reproduced("A06 equivalent valid decimal values compare unequal", equal.equals(false));

        AppSpec malformed = AppSpec.Companion.parse(plain().put("actions", new JSONObject().put("lost","not an array")).toString());
        reproduced("A07 malformed action collection is silently dropped", malformed.getActions().isEmpty());

        Path cache = Files.createTempDirectory("buildai-audit-export-");
        boolean exportFailed = false;
        try { Files.writeString(cache.resolve("BuildAI_Trip / budget.txt"), "Example export"); }
        catch (java.io.IOException expected) { exportFailed = true; }
        finally { Files.delete(cache); }
        reproduced("A08 project title with slash makes export filename unwritable", exportFailed);
        System.out.println("TOTAL: " + count + " defects reproduced; production source unchanged.");
    }
}
