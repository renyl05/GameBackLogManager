package gbm;

import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import javax.net.ssl.SSLSession;
import static gbm.Json.*;

/** End-to-end fixtures only: never sends a password or console token to a live service. */
public final class ConsoleTest {
  static void ok(boolean value,String message){IntegrationTest.check(value,message);}
  public static void main(String[] args)throws Exception{
    Properties cfg=new Properties();cfg.setProperty("GBM_TEST_ISOLATED","true");cfg.setProperty("XBOX_CLIENT_ID","fixture-client");cfg.setProperty("IGDB_CLIENT_ID","fixture-igdb");cfg.setProperty("IGDB_CLIENT_SECRET","fixture-secret");
    Fake fake=new Fake();Providers providers=new Providers(cfg,fake);Path db=Files.createTempDirectory("gbm-console").resolve("db.json");
    try(Server server=new Server(db,0,cfg,providers)){
      server.start();Api a=new Api("http://127.0.0.1:"+server.port()),b=new Api(a.base),anonymous=new Api(a.base);
      Map<String,Object> login=a.call("register",obj("email","console-a@example.invalid","username","console-a","password","console-tests-123"));a.token=str(login,"token");String uid=str(map(login.get("user")),"id");
      b.token=str(b.call("register",obj("email","console-b@example.invalid","username","console-b","password","console-tests-123")),"token");
      try{anonymous.call("console-start",obj("platform","Xbox"));throw new AssertionError("anonymous allowed");}catch(Api.SessionExpired|SecurityException expected){}catch(IllegalStateException expected){}
      ok(fake.calls==0,"console endpoints require an app session before calling providers");
      a.call("console-psn",obj("platform","PlayStation","npsso","a".repeat(64)));
      Map<String,Object> state=a.call("state",obj());Map<String,Object> ps=map(list(state.get("consoleLinks")).get(0));
      ok(yes(ps,"verified")&&list(ps.get("games")).size()==2,"PSN OAuth exchange and paginated history imported");
      ok(list(state.get("entries")).isEmpty(),"played titles never become owned editions automatically");
      ok(list(b.call("state",obj()).get("consoleLinks")).isEmpty(),"console history remains private to the owner");
      ok(list(b.call("state",obj()).get("games")).stream().map(Json::map).noneMatch(g->g.containsKey("earned")),"private progress never enters shared catalog metadata");
      String snapshot=Files.readString(db),export=write(a.call("export",obj()));
      ok(!snapshot.contains("fixture-psn-token")&&!snapshot.contains("a".repeat(64))&&!export.contains("fixture-psn-token"),"PSN credentials excluded from storage and export");
      a.call("console-start",obj("platform","Xbox"));server.consoles.devices.get(uid).next=0;
      ok(yes(a.call("console-poll",obj("platform","Xbox")),"pending"),"Xbox authorization_pending is not an error");
      server.consoles.devices.get(uid).next=0;
      a.call("console-poll",obj("platform","Xbox"));
      state=a.call("state",obj());ok(list(state.get("consoleLinks")).size()==2,"Xbox OAuth, user token and XSTS history coexist with PSN");
      ok(!Files.readString(db).contains("fixture-xsts-token"),"Xbox tokens are session-only");
      List<Object> imported=List.of(obj("name","Mario","platform","Nintendo Switch"),obj("name","Mario","platform","Nintendo Switch"));
      a.call("console-import",obj("platform","Nintendo","games",imported));
      a.call("console-import",obj("platform","Nintendo","games",imported));
      Map<String,Object> nintendo=list(a.call("state",obj()).get("consoleLinks")).stream().map(Json::map).filter(v->str(v,"platform").equals("Nintendo")).findFirst().orElseThrow();
      ok(!yes(nintendo,"verified")&&str(nintendo,"mode").equals("manual")&&list(nintendo.get("games")).size()==1,"Nintendo manual import deduplicated and never marked authenticated");
      try{a.call("console-import",obj("platform","Nintendo","games",List.of(obj("name","Good","platform","Nintendo Switch"),obj("name","Bad","platform","Unknown"))));throw new AssertionError("invalid accepted");}catch(IllegalStateException expected){}
      ok(list(a.call("state",obj()).get("consoleLinks")).size()==3,"invalid import leaves all existing links intact");
      a.call("console-unlink",obj("platform","PlayStation"));ok(!server.consoles.connected(uid,"PlayStation")&&list(a.call("state",obj()).get("consoleLinks")).size()==2,"unlink revokes only the selected console");
      synchronized(server){server.db.rows("games").add(obj("id","local:metadata-test","name","Test Game","platforms","PC","cover","","summary",""));}
      Map<String,Object> enriched=map(a.call("game-metadata",obj("game","local:metadata-test")).get("game"));
      ok(str(enriched,"id").equals("local:metadata-test")&&!str(enriched,"cover").isBlank(),"IGDB metadata enrichment preserves existing game references");
      a.call("logout",obj());ok(!server.consoles.connected(uid,"Xbox"),"logout clears console session credentials");
    }
    Store restored=new Store(db,cfg);ok(restored.rows("consoleLinks").size()==2,"imported history persists without authentication tokens");
    System.out.println("PASS initial console integrations, privacy, atomic imports, catalog metadata");
  }
  static final class Fake extends ProviderTest.Fake {
    int calls,polls;
    @Override public <T> HttpResponse<T> send(HttpRequest request,HttpResponse.BodyHandler<T> handler)throws java.io.IOException,InterruptedException{
      String host=request.uri().getHost(),path=request.uri().getPath();String body;int status=200;Map<String,List<String>> headers=Map.of();calls++;
      if(host.equals("ca.account.sony.com")&&path.endsWith("/authorize")){
        ok(request.headers().firstValue("Cookie").orElse("").equals("npsso="+"a".repeat(64)),"PSN cookie sent only to Sony authorization host");
        status=302;headers=Map.of("Location",List.of("com.scee.psxandroid.scecompcall://redirect/?code=fixture-code"));body="{}";
      }else if(host.equals("ca.account.sony.com")&&path.endsWith("/token"))body="{\"access_token\":\"fixture-psn-token\",\"expires_in\":7200}";
      else if(host.equals("m.np.playstation.com")){
        boolean first=request.uri().getQuery().contains("offset=0");
        body=write(obj("totalItemCount",2,"trophyTitles",List.of(obj("npCommunicationId",first?"NPWRTEST_01":"NPWRTEST_02","trophyTitleName",first?"PSN Game 1":"PSN Game 2","trophyTitlePlatform","PS5","earnedTrophies",obj("bronze",2),"definedTrophies",obj("bronze",5)))));
      }else if(host.equals("login.microsoftonline.com")&&path.endsWith("/devicecode"))body=write(obj("device_code","fixture-device","user_code","ABCD-EFGH","verification_uri","https://microsoft.com/devicelogin","expires_in",900,"interval",5));
      else if(host.equals("login.microsoftonline.com")&&path.endsWith("/token")){
        if(polls++==0){status=400;body="{\"error\":\"authorization_pending\"}";}else body="{\"access_token\":\"fixture-microsoft-token\"}";
      }else if(host.equals("user.auth.xboxlive.com"))body="{\"Token\":\"fixture-user-token\"}";
      else if(host.equals("xsts.auth.xboxlive.com"))body=write(obj("Token","fixture-xsts-token","NotAfter",Instant.now().plusSeconds(3600).toString(),"DisplayClaims",obj("xui",List.of(obj("uhs","12345","xid","987654321")))));
      else if(host.equals("achievements.xboxlive.com"))body=write(obj("titles",List.of(obj("titleId","123","name","Xbox Game","currentAchievements",3,"totalAchievements",10)),"pagingInfo",obj()));
      else return super.send(request,handler);
      return response(request,status,body,headers);
    }
  }
  @SuppressWarnings("unchecked") static <T> HttpResponse<T> response(HttpRequest request,int status,String body,Map<String,List<String>> headers){return new HttpResponse<T>(){
    public int statusCode(){return status;}public HttpRequest request(){return request;}public Optional<HttpResponse<T>> previousResponse(){return Optional.empty();}public HttpHeaders headers(){return HttpHeaders.of(headers,(a,b)->true);}public T body(){return (T)body;}public Optional<SSLSession> sslSession(){return Optional.empty();}public URI uri(){return request.uri();}public HttpClient.Version version(){return HttpClient.Version.HTTP_1_1;}
  };}
}
