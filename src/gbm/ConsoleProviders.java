package gbm;

import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static gbm.Json.*;

/** Experimental console adapters. Session credentials never enter persistent account data. */
final class ConsoleProviders {
  final HttpClient http;final Properties config;
  record Auth(String token,String account,long until){}
  static final class Device {
    final String code;final long until;long next;int interval;
    Device(String code,long until,int interval){this.code=code;this.until=until;this.interval=interval;next=System.currentTimeMillis()+interval*1000L;}
  }
  final Map<String,Auth> auth=new ConcurrentHashMap<>();
  final Map<String,Device> devices=new ConcurrentHashMap<>();
  ConsoleProviders(Properties config){this(config,HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build());}
  ConsoleProviders(Properties config,HttpClient http){this.config=config;this.http=http;}
  String setting(String name){return SnapshotDatabase.setting(config,name,"");}
  boolean xboxReady(){return !setting("XBOX_CLIENT_ID").isBlank();}
  String form(Map<String,Object> data){StringJoiner out=new StringJoiner("&");data.forEach((k,v)->out.add(Providers.enc(k)+"="+Providers.enc(Objects.toString(v))));return out.toString();}
  HttpRequest.Builder request(String url){return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).header("Accept","application/json");}
  Map<String,Object> send(HttpRequest request,boolean allowPending)throws Exception{
    HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString());
    Map<String,Object> data;
    try{data=map(Json.read(response.body()));}catch(Exception e){throw new IllegalStateException("Risposta console non valida (HTTP "+response.statusCode()+").");}
    if(response.statusCode()/100!=2&&!(allowPending&&Set.of("authorization_pending","slow_down").contains(str(data,"error"))))
      throw new IllegalStateException("Accesso console non riuscito (HTTP "+response.statusCode()+"). Verifica autorizzazione, privacy e scadenza della sessione.");
    return data;
  }
  Map<String,Object> post(String url,Map<String,Object> data,boolean pending)throws Exception{
    return send(request(url).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form(data))).build(),pending);
  }
  Map<String,Object> jsonPost(String url,Map<String,Object> data)throws Exception{
    return send(request(url).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(write(data))).build(),false);
  }
  void cleanup(){long now=System.currentTimeMillis();auth.entrySet().removeIf(e->e.getValue().until<now);devices.entrySet().removeIf(e->e.getValue().until<now);}
  void clear(String user,String platform){auth.remove(user+":"+platform);if(platform.equals("Xbox"))devices.remove(user);}
  void logout(String user){clear(user,"PlayStation");clear(user,"Xbox");}
  boolean connected(String user,String platform){cleanup();return auth.containsKey(user+":"+platform);}

  Map<String,Object> startXbox(String user)throws Exception{
    if(!xboxReady())throw new IllegalStateException("Collegamento Xbox da configurare: manca XBOX_CLIENT_ID sul server.");
    cleanup();Map<String,Object> result=post("https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode",obj("client_id",setting("XBOX_CLIENT_ID"),"scope","XboxLive.signin"),false);
    String code=str(result,"device_code"),url=str(result,"verification_uri"),shortCode=str(result,"user_code");
    URI uri=URI.create(url);
    if(code.isBlank()||shortCode.isBlank()||!"https".equals(uri.getScheme())||!Set.of("microsoft.com","www.microsoft.com","login.microsoftonline.com").contains(uri.getHost()))throw new IllegalStateException("Avvio accesso Microsoft non valido.");
    int seconds=(int)Math.max(5,Math.min(60,num(result,"interval")));
    devices.put(user,new Device(code,System.currentTimeMillis()+Math.min(900,Math.max(60,num(result,"expires_in")))*1000L,seconds));
    return obj("url",url,"code",shortCode,"interval",seconds,"pending",true);
  }
  Map<String,Object> pollXbox(String user)throws Exception{
    cleanup();Device device=devices.get(user);if(device==null)throw new IllegalStateException("Accesso Xbox scaduto: ricomincia il collegamento.");
    synchronized(device){
      long now=System.currentTimeMillis();if(now<device.next)return obj("pending",true,"interval",device.interval);
      device.next=now+device.interval*1000L;
      Map<String,Object> result=post("https://login.microsoftonline.com/consumers/oauth2/v2.0/token",obj("client_id",setting("XBOX_CLIENT_ID"),"grant_type","urn:ietf:params:oauth:grant-type:device_code","device_code",device.code),true);
      if(Set.of("authorization_pending","slow_down").contains(str(result,"error"))){if(str(result,"error").equals("slow_down")){device.interval+=5;device.next=now+device.interval*1000L;}return obj("pending",true,"interval",device.interval);}
      String access=str(result,"access_token");if(access.isBlank())throw new IllegalStateException("Autorizzazione Microsoft incompleta.");
      Map<String,Object> userToken=jsonPost("https://user.auth.xboxlive.com/user/authenticate",obj("Properties",obj("AuthMethod","RPS","SiteName","user.auth.xboxlive.com","RpsTicket","d="+access),"RelyingParty","http://auth.xboxlive.com","TokenType","JWT"));
      Map<String,Object> xsts=jsonPost("https://xsts.auth.xboxlive.com/xsts/authorize",obj("Properties",obj("SandboxId","RETAIL","UserTokens",List.of(str(userToken,"Token"))),"RelyingParty","http://xboxlive.com","TokenType","JWT"));
      List<Object> claims=list(map(xsts.get("DisplayClaims")).get("xui"));
      if(claims.isEmpty()||str(xsts,"Token").isBlank())throw new IllegalStateException("L’account non restituisce un’autorizzazione Xbox valida.");
      Map<String,Object> claim=map(claims.get(0));String xuid=str(claim,"xid"),uhs=str(claim,"uhs");
      if(!xuid.matches("[0-9]{1,20}")||uhs.isBlank())throw new IllegalStateException("Identità Xbox non valida.");
      long expires;try{expires=Instant.parse(str(xsts,"NotAfter")).toEpochMilli();}catch(Exception e){throw new IllegalStateException("Scadenza Xbox non valida.");}
      auth.put(user+":Xbox",new Auth("XBL3.0 x="+uhs+";"+str(xsts,"Token"),xuid,expires));devices.remove(user);
      return obj("pending",false);
    }
  }

  void connectPsn(String user,String npsso)throws Exception{
    if(!npsso.matches("[A-Za-z0-9_-]{64}"))throw new IllegalArgumentException("Il codice NPSSO deve contenere 64 caratteri.");
    String base="https://ca.account.sony.com/api/authz/v3/oauth";
    String query=form(obj("access_type","offline","client_id","09515159-7237-4370-9b40-3806e67c0891","redirect_uri","com.scee.psxandroid.scecompcall://redirect","response_type","code","scope","psn:mobile.v2.core psn:clientapp"));
    HttpResponse<String> response=http.send(request(base+"/authorize?"+query).header("Cookie","npsso="+npsso).GET().build(),HttpResponse.BodyHandlers.ofString());
    String location=response.headers().firstValue("Location").orElse("");
    if(response.statusCode()/100!=3||!location.startsWith("com.scee.psxandroid.scecompcall://redirect"))throw new IllegalStateException("Codice PlayStation non valido o scaduto.");
    URI target=URI.create(location);String code="";
    if(target.getRawQuery()!=null)for(String pair:target.getRawQuery().split("&")){String[] part=pair.split("=",2);if(part[0].equals("code")&&part.length==2)code=URLDecoder.decode(part[1],java.nio.charset.StandardCharsets.UTF_8);}
    if(code.isBlank())throw new IllegalStateException("PlayStation non ha autorizzato il collegamento.");
    // Public mobile-client parameters documented by the upstream psn-api project.
    Map<String,Object> tokens=send(request(base+"/token").header("Authorization","Basic MDk1MTUxNTktNzIzNy00MzcwLTliNDAtMzgwNmU2N2MwODkxOnVjUGprYTV0bnRCMktxc1A=").header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form(obj("code",code,"redirect_uri","com.scee.psxandroid.scecompcall://redirect","grant_type","authorization_code","token_format","jwt")))).build(),false);
    if(str(tokens,"access_token").isBlank()||num(tokens,"expires_in")<=0)throw new IllegalStateException("Token PlayStation non disponibile.");
    auth.put(user+":PlayStation",new Auth("Bearer "+str(tokens,"access_token"),"me",System.currentTimeMillis()+Math.min(7200,num(tokens,"expires_in"))*1000L));
  }
  Map<String,Object> titles(String user,String platform)throws Exception{
    cleanup();Auth session=auth.get(user+":"+platform);if(session==null)throw new IllegalStateException("Ricollega "+platform+": la sessione non è più disponibile.");
    List<Object> games=new ArrayList<>();String continuation="";int offset=0;
    long deadline=System.currentTimeMillis()+45000;
    for(int page=0;page<10&&System.currentTimeMillis()<deadline;page++){
      boolean ps=platform.equals("PlayStation");
      String url=ps?"https://m.np.playstation.com/api/trophy/v1/users/me/trophyTitles?limit=100&offset="+offset:"https://achievements.xboxlive.com/users/xuid("+session.account+")/history/titles?maxItems=100"+(continuation.isBlank()?"":"&continuationToken="+Providers.enc(continuation));
      Map<String,Object> response=send(request(url).header("Authorization",session.token).header("x-xbl-contract-version","2").GET().build(),false);
      List<Object> pageGames=list(response.get(ps?"trophyTitles":"titles"));
      for(Object item:pageGames){Map<String,Object> value=map(item);games.add(ps?psnGame(value):xboxGame(value));}
      offset+=pageGames.size();continuation=str(map(response.get("pagingInfo")),"continuationToken");
      if(ps?(pageGames.isEmpty()||(response.containsKey("totalItemCount")?offset>=num(response,"totalItemCount"):pageGames.size()<100)):continuation.isBlank())return obj("platform",platform,"account",session.account,"games",games,"verified",true,"mode","api","partial",false);
    }
    return obj("platform",platform,"account",session.account,"games",games,"verified",true,"mode","api","partial",true);
  }
  static long count(Object value){Map<String,Object> v=map(value);return num(v,"bronze")+num(v,"silver")+num(v,"gold")+num(v,"platinum");}
  static Map<String,Object> psnGame(Map<String,Object> value){
    String nativeId=str(value,"npCommunicationId"),name=str(value,"trophyTitleName");if(nativeId.isBlank()||name.isBlank())throw new IllegalStateException("Titolo PlayStation incompleto.");
    String platform=str(value,"trophyTitlePlatform").replace("PS5","PlayStation 5").replace("PS4","PlayStation 4").replace("PS3","PlayStation 3").replace("PSVITA","PlayStation Vita");
    return obj("id","psn:"+nativeId,"name",name,"platforms",platform,"cover",str(value,"trophyTitleIconUrl"),"hero","","summary","Titolo presente nella cronologia trofei PlayStation. La cronologia non dimostra il possesso del gioco.","source","PlayStation","earned",count(value.get("earnedTrophies")),"total",count(value.get("definedTrophies")));
  }
  static Map<String,Object> xboxGame(Map<String,Object> value){
    String nativeId=str(value,"titleId"),name=str(value,"name");if(nativeId.isBlank()||name.isBlank())throw new IllegalStateException("Titolo Xbox incompleto.");
    return obj("id","xbox:"+nativeId,"name",name,"platforms","Xbox","cover","","hero","","summary","Titolo presente nella cronologia obiettivi Xbox. La cronologia non è la libreria completa e non dimostra il possesso.","source","Xbox","earned",num(value,"currentAchievements"),"total",num(value,"totalAchievements"));
  }
}
