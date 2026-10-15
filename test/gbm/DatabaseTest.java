package gbm;
import java.nio.file.*;
import java.util.*;
/** H2 PostgreSQL-compatible SQL test; live Neon credentials are deliberately not used. */
public class DatabaseTest {
  static void ok(boolean b,String name){IntegrationTest.check(b,name);}
  public static void main(String[]args)throws Exception{
    try{Class.forName("org.h2.Driver");}catch(ClassNotFoundException unavailable){System.out.println("SKIP SQL tests: add H2 to test classpath; it is not a production dependency.");return;}
    Path dir=Files.createTempDirectory("gbm-db-test");Properties cfg=new Properties();
    cfg.setProperty("GBM_TEST_ISOLATED","true");cfg.setProperty("JDBC_DATABASE_URL","jdbc:h2:"+dir.resolve("postgres-fixture")+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
    Store first=new Store(dir.resolve("must-not-exist.json"),cfg);
    first.rows("users").add(Json.obj("id","sample","username","test"));first.save();
    Store restarted=new Store(dir.resolve("must-not-exist.json"),cfg);
    ok(restarted.rows("users").size()==1,"SQL snapshot survives new store instance");
    ok(!Files.exists(dir.resolve("must-not-exist.json")),"remote persistence never writes local fallback");
    first.rows("reviews").add(Json.obj("text","è UTF-8 \" sicuro"));first.save();
    try{restarted.save();throw new AssertionError("lost update");}catch(java.io.IOException expected){ok(true,"stale writer cannot overwrite remote snapshot");}
    Store after=new Store(dir.resolve("absent.json"),cfg);
    ok(after.rows("reviews").size()==1,"conflicting writer leaves stored data intact");
    Properties required=new Properties();required.setProperty("GBM_TEST_ISOLATED","true");required.setProperty("REQUIRE_DATABASE","true");
    try{new Server(dir.resolve("bad.json"),0,required);throw new AssertionError("cloud needs db");}catch(IllegalStateException expected){ok(true,"cloud startup refuses missing database");}
    Properties invalid=new Properties();invalid.setProperty("GBM_TEST_ISOLATED","true");invalid.setProperty("JDBC_DATABASE_URL","jdbc:postgresql://localhost/db?sslmode=disable");
    try{new Store(dir.resolve("insecure.json"),invalid);throw new AssertionError("TLS override");}catch(java.io.IOException expected){ok(true,"JDBC URL cannot override verified TLS");}
    try(Server server=new Server(dir.resolve("local.json"),0,requiredLocal())){server.start();java.net.http.HttpResponse<String>r=java.net.http.HttpClient.newHttpClient().send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+server.port()+"/health")).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());ok(r.statusCode()==200,"Render GET health probe");}
    System.out.println("PASS SQL compatibility tests; live PostgreSQL/Neon still requires deployment test.");
  }
  static Properties requiredLocal(){Properties p=new Properties();p.setProperty("GBM_TEST_ISOLATED","true");return p;}
}
