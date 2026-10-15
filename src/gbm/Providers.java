package gbm;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
/** Provider credentials remain on the server. */
public final class Providers {
  final HttpClient http;
  final Properties cfg;
  String igdbToken = "";
  long expires;
  public Providers(Properties c) {
    this(c, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).build());
  }
  Providers(Properties c, HttpClient http) {
    cfg = c;
    this.http = http;
  }
  String config(String k) {return SnapshotDatabase.setting(cfg,k,"");}

  public boolean igdbReady() {
    return ! config("IGDB_CLIENT_ID").isBlank() && ! config("IGDB_CLIENT_SECRET").isBlank();
  }
  public boolean steamReady() {
    return ! config("STEAM_API_KEY").isBlank();
  }
  public Object request(HttpRequest r) throws Exception {
    HttpResponse < String > a = http.send(r, HttpResponse.BodyHandlers.ofString());
    if (a.statusCode() / 100 != 2) throw new IllegalStateException("Il servizio esterno ha risposto con HTTP " + a.statusCode());
    return Json.read(a.body());
  }
  public static String enc(String s) {
    return URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
  }
  public synchronized List < Object > catalog(String search, String mode, int offset) throws Exception {
    if (! igdbReady()) throw new IllegalStateException("Catalogo esteso non configurato: aggiungi le credenziali IGDB nel file server.properties.");
    if (System.currentTimeMillis() >= expires) {
      Map < String, Object > t = Json.map(request(HttpRequest.newBuilder(URI.create("https://id.twitch.tv/oauth2/token")).timeout(Duration.ofSeconds(20)).header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString("client_id=" + enc(config("IGDB_CLIENT_ID")) + "&client_secret=" + enc(config("IGDB_CLIENT_SECRET")) + "&grant_type=client_credentials")).build()));
      igdbToken = Json.str(t, "access_token");
      expires = System.currentTimeMillis() + Math.max(30, Json.num(t, "expires_in") - 60) * 1000;
    }
    String safe = search.replace("\\", "\\\\").replace("\"", "\\\"").replaceAll("[\\p{Cntrl}]", "");
    String q = "fields " + "name,summary,cover.image_id,screenshots.image_id,platforms.name,genres.name,first_" + "release_date,total_rating_count; limit 36; offset " + offset + "; ";
    if (! safe.isBlank()) q += "search \"" + safe + "\"; ";
    else q += "sort total_rating_count desc; ";
    // Retro is an explicit collection of legacy platform IDs, not an arbitrary release-year cutoff.
    if (mode.equals("retro")) q += "where platforms = (4,5,7,8,9,11,12,18,19,20,21,22,23,24,29,30,32,33,35,37,38); ";
    Object data = request(HttpRequest.newBuilder(URI.create("https://api.igdb.com/v4/games")).timeout(Duration.ofSeconds(25)).header("Client-ID", config("IGDB_CLIENT_ID")).header("Authorization", "Bearer " + igdbToken).POST(HttpRequest.BodyPublishers.ofString(q)).build());
    List < Object > games = new ArrayList < >();
    for (Object o : Json.list(data)) {
      Map < String, Object > g = Json.map(o);
      String image = Json.str(Json.map(g.get("cover")), "image_id");
      List < Object > screens = Json.list(g.get("screenshots"));
      String hero = screens.isEmpty() ? "" : Json.str(Json.map(screens.get(0)), "image_id");
      long date = Json.num(g, "first_release_date");
      games.add(Json.obj("id", "igdb:" + Json.num(g, "id"), "name", Json.str(g, "name"), "summary", Json.str(g, "summary"), "platforms", names(g.get("platforms")), "genres", names(g.get("genres")), "cover", image.isBlank() ? "" : "https://images.igdb.com/igdb/image/upload/t_cover_big_2x/" + image + ".jpg", "hero", hero.isBlank() ? "" : "https://images.igdb.com/igdb/image/upload/t_1080p/" + hero + ".jpg", "year", date == 0 ? "" : Instant.ofEpochSecond(date).atZone(ZoneOffset.UTC).getYear(), "source", "IGDB"));
    }
    return games;
  }
  String names(Object a) {
    StringJoiner j = new StringJoiner(", ");
    for (Object o : Json.list(a)) j.add(Json.str(Json.map(o), "name"));
    return j.toString();
  }
  Object steam(String endpoint, String extra) throws Exception {
    return request(HttpRequest.newBuilder(URI.create("https://api.steampowered.com/" + endpoint + "?key=" + enc(config("STEAM_API_KEY")) + "&" + extra)).timeout(Duration.ofSeconds(25)).GET().build());
  }
  public List < Object > steamGames(String id) throws Exception {
    if (! steamReady()) throw new IllegalStateException("Chiave Steam Web API non configurata sul server.");
    Map < String, Object > response = Json.map(Json.map(steam("IPlayerService/GetOwnedGames/v1/", "steamid=" + enc(id) + "&include_appinfo=1&include_played_free_games=1")).get("response"));
    if (! response.containsKey("games")) throw new IllegalStateException("Steam non restituisce i giochi: verifica la visibilità dei dettagli di gioco del profilo.");
    return Json.list(response.get("games"));
  }
  public List < Object > achievements(String steamId, String app) throws Exception {
    if (! app.matches("[0-9]+")) throw new IllegalArgumentException("ID Steam non valido");
    Map < String, Object > stats = Json.map(Json.map(steam("ISteamUserStats/GetPlayerAchievements/v1/", "steamid=" + enc(steamId) + "&appid=" + app + "&l=italian")).get("playerstats"));
    if (! Json.yes(stats, "success")) throw new IllegalStateException("Obiettivi non disponibili per questo gioco o profilo.");
    Map < String, Object > schema = Json.map(Json.map(steam("ISteamUserStats/GetSchemaForGame/v2/", "appid=" + app + "&l=italian")).get("game"));
    Map < String, Map < String, Object >> defs = new HashMap < >();
    for (Object o : Json.list(Json.map(schema.get("availableGameStats")).get("achievements"))) {
      Map < String, Object > d = Json.map(o);
      defs.put(Json.str(d, "name"), d);
    }
    List < Object > out = new ArrayList < >();
    for (Object o : Json.list(stats.get("achievements"))) {
      Map < String, Object > a = Json.map(o), d = defs.getOrDefault(Json.str(a, "apiname"), Map.of());
      out.add(Json.obj("id", Json.str(a, "apiname"), "name", d.getOrDefault("displayName", a.get("apiname")), "description", Json.str(d, "description"), "unlocked", Json.num(a, "achieved") == 1, "time", Json.num(a, "unlocktime"), "icon", Json.str(d, Json.num(a, "achieved") == 1 ? "icon" : "icongray"), "platform", "Steam"));
    }
    return out;
  }
}
