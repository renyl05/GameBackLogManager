package gbm;

import java.util.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import static gbm.Json.*;

/** Console history is private and independent of the Steam link and owned editions. */
final class ConsoleRoutes {
  final Server server;final ConsoleProviders providers;
  ConsoleRoutes(Server server,ConsoleProviders providers){this.server=server;this.providers=providers;}
  Map<String,Object> dispatch(String route,Map<String,Object> input,String token)throws Exception{
    String user;synchronized(server){user=str(server.user(token),"id");server.rate("console:"+user);}
    String platform=str(input,"platform");
    if(!Set.of("PlayStation","Xbox","Nintendo").contains(platform))throw new IllegalArgumentException("Console non valida.");
    Map<String,Object> result;
    switch(route){
      case "console-start" -> {if(!platform.equals("Xbox"))throw new IllegalArgumentException("Accesso browser disponibile per Xbox.");result=providers.startXbox(user);}
      case "console-poll" -> {if(!platform.equals("Xbox"))throw new IllegalArgumentException("Console non valida.");result=providers.pollXbox(user);if(!yes(result,"pending"))result=providers.titles(user,platform);}
      case "console-psn" -> {if(!platform.equals("PlayStation"))throw new IllegalArgumentException("Console non valida.");providers.connectPsn(user,str(input,"npsso").trim());result=providers.titles(user,platform);}
      case "console-sync" -> {if(platform.equals("Nintendo"))throw new IllegalArgumentException("Nintendo: sincronizzazione automatica non disponibile.");result=providers.titles(user,platform);}
      case "console-import" -> {if(!platform.equals("Nintendo"))throw new IllegalArgumentException("Importazione manuale disponibile per Nintendo.");result=nintendo(input);}
      case "console-cancel" -> {synchronized(server){server.user(token);providers.devices.remove(user);}return obj("ok",true);}
      case "console-unlink" -> {
        synchronized(server){server.user(token);providers.clear(user,platform);server.db.rows("consoleLinks").removeIf(o->str(map(o),"user").equals(user)&&str(map(o),"platform").equals(platform));server.db.save();}
        return obj("ok",true);
      }
      default -> throw new IllegalArgumentException("Operazione console non valida.");
    }
    synchronized(server){
      try{server.user(token);}catch(SecurityException e){providers.clear(user,platform);throw e;}
      if(result.containsKey("games")){
        for(Object item:list(result.get("games"))){Map<String,Object> game=new LinkedHashMap<>(map(item));game.remove("earned");Map<String,Object> cached=server.db.find("games","id",str(game,"id"));if(cached==null)server.db.rows("games").add(new LinkedHashMap<>(game));else cached.putAll(game);}
        server.db.rows("consoleLinks").removeIf(o->str(map(o),"user").equals(user)&&str(map(o),"platform").equals(platform));
        Map<String,Object> link=new LinkedHashMap<>(result);link.put("user",user);link.put("syncedAt",System.currentTimeMillis());server.db.rows("consoleLinks").add(link);server.db.save();
        return obj("ok",true,"count",list(result.get("games")).size(),"pending",false,"partial",yes(result,"partial"));
      }
      return result;
    }
  }
  List<Object> state(String user){
    List<Object> links=new ArrayList<>();
    for(Object item:server.ownedRows("consoleLinks",user)){Map<String,Object> link=new LinkedHashMap<>(map(item));link.put("connected",providers.connected(user,str(link,"platform")));links.add(link);}
    return links;
  }
  static Map<String,Object> nintendo(Map<String,Object> input)throws Exception{
    List<Object> rows=list(input.get("games"));if(rows.isEmpty()||rows.size()>200)throw new IllegalArgumentException("Importa da 1 a 200 giochi per file.");
    List<Object> games=new ArrayList<>();Set<String> seen=new HashSet<>();
    for(Object item:rows){Map<String,Object> row=map(item);String name=str(row,"name").trim(),platform=str(row,"platform").trim();
      if(name.isBlank()||name.length()>200||name.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Nome gioco non valido.");
      if(!Set.of("Nintendo Switch","Nintendo Switch 2","Nintendo 3DS","Nintendo DS","Wii","Wii U","Game Boy","Game Boy Advance","Nintendo Entertainment System","Super Nintendo","Nintendo 64","GameCube").contains(platform))throw new IllegalArgumentException("Piattaforma Nintendo non valida: "+platform);
      String key=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((name.toLowerCase(Locale.ROOT)+"|"+platform).getBytes(StandardCharsets.UTF_8))).substring(0,24);
      if(seen.add(key))games.add(obj("id","nintendo:"+key,"name",name,"platforms",platform,"cover","","hero","","summary","Titolo inserito tramite importazione manuale Nintendo; dati dichiarati dall’utente.","source","Nintendo manuale"));
    }
    return obj("platform","Nintendo","account","","mode","manual","verified",false,"partial",false,"games",games);
  }
}
