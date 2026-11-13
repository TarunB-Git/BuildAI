import com.buildai.app.builder.*;
import org.json.*;
import java.nio.file.*;
public class DumpPlanningGrammar {
 public static void main(String[] args) throws Exception {
  JSONObject brief=new JSONObject("{\"turns\":[{\"messageId\":\"u1\",\"text\":\"Log meals\"}]}");
  for(int i=0;i<2;i++) {
   if(i==1) brief.put("questions",new JSONArray().put(new JSONObject().put("id","mealFields").put("question","What should a meal remember?")));
   Files.writeString(Path.of(args[0],"planning-"+i+".gbnf"),PlanningGrammar.INSTANCE.forBrief(ProjectBrief.Companion.read(brief)));
  }
 }
}
