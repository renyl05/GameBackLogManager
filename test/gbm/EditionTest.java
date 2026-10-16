package gbm;
import java.nio.file.*;import java.util.*;
public class EditionTest {
 static void check(boolean b,String s){IntegrationTest.check(b,s);}
 static Map<String,Object> entry(Api api)throws Exception{return Json.map(Json.list(api.call("state",Json.obj()).get("entries")).get(0));}
 public static void main(String[]args)throws Exception{
  Path dir=Files.createTempDirectory("gbm-editions");Properties cfg=new Properties();cfg.setProperty("GBM_TEST_ISOLATED","true");String user;
  try(Server server=new Server(dir.resolve("db.json"),0,cfg)){
   server.start();Api a=new Api("http://127.0.0.1:"+server.port());Map<String,Object> registered=a.call("register",Json.obj("email","edition@example.com","username","edition","password","Edition-test-123"));a.token=Json.str(registered,"token");user=Json.str(Json.map(registered.get("user")),"id");
   for(String platform:List.of("PC","PlayStation 5","PC"))a.call("entry",Json.obj("game","local:minecraft","platform",platform,"status",platform.equals("PC")?"FINITO":"IN_CORSO","favorite",true,"rating",9));
   Map<String,Object> e=entry(a);check(Json.list(e.get("editions")).size()==2,"platform upsert never duplicates an edition");
   check(Json.str(e,"status").equals("IN_CORSO"),"aggregate keeps active edition");
   a.call("entry",Json.obj("game","local:minecraft","status","IN_CORSO","favorite",false,"rating",8));check(Json.list(entry(a).get("editions")).size()==2,"legacy favorite update preserves all editions");
   IntegrationTest.denied(a,"entry",Json.obj("game","local:minecraft","platform","Fake console","status","FINITO"),"unsupported edition rejected");
   a.call("entry",Json.obj("game","local:minecraft","platform","PC","status","","favorite",false,"rating",8));e=entry(a);check(Json.list(e.get("editions")).size()==1&&Json.str(Json.map(Json.list(e.get("editions")).get(0)),"platform").equals("PlayStation 5"),"removing PC preserves PlayStation edition");
   Api viewer=new Api(a.base);viewer.token=Json.str(viewer.call("register",Json.obj("email","viewer@example.com","username","viewer","password","Viewer-test-123")),"token");check(Json.list(viewer.call("profile",Json.obj("user",user)).get("entries")).isEmpty(),"private edition ownership stays private");
   a.call("settings",Json.obj("visibility","public"));check(Json.list(Json.map(Json.list(viewer.call("profile",Json.obj("user",user)).get("entries")).get(0)).get("editions")).size()==1,"public profile exposes actual owned editions");
  }
  Store restored=new Store(dir.resolve("db.json"),cfg);check(Json.list(Json.map(restored.rows("entries").get(0)).get("editions")).size()==1,"editions survive restart");
  Path legacy=dir.resolve("legacy.json");Files.writeString(legacy,Json.write(Json.obj("games",List.of(Json.obj("id","old","platforms","PC, PlayStation 5")),"entries",List.of(Json.obj("user","u","game","old","status","FINITO")))));Store migrated=new Store(legacy,cfg);check(Json.str(Json.map(Json.list(Json.map(migrated.rows("entries").get(0)).get("editions")).get(0)),"platform").equals("Edizione non specificata"),"legacy multi-platform ownership is not guessed");
  System.out.println("PASS edition persistence and privacy");
 }
}
