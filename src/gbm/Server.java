package gbm;
import com.sun.net.httpserver.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.io.*;
/** Single-node backend. For internet hosting use HTTPS reverse proxy + persistent disk. */
public final class Server implements AutoCloseable {
  public final Store db;
  final Providers providers;
  final HttpServer server;
  final ExecutorService workers = Executors.newFixedThreadPool(8);
  final Map < String, Session > sessions = new ConcurrentHashMap < >();
  final Map < String, Pending > pending = new HashMap < >();
  final Map < String, Deque < Long >> attempts = new HashMap < >();
  final Map < String, Reset > resets = new HashMap < >();
  final Mailer mailer;
  final String publicBase;
  final SecureRandom random = new SecureRandom();
  record Reset(String user, String code, long until, int tries) {
  }
  record Session(String user, long until) {
  }
  record Pending(String user, long until, String callback) {
  }
  public Server(Path path, int port, Properties cfg) throws Exception {
    this(path, port, cfg, new Providers(cfg));
  }
  Server(Path path, int port, Properties cfg, Providers provider) throws Exception {
    if(Boolean.parseBoolean(SnapshotDatabase.setting(cfg,"REQUIRE_DATABASE","false"))&&SnapshotDatabase.setting(cfg,"JDBC_DATABASE_URL","").isBlank())throw new IllegalStateException("Configurazione cloud incompleta: JDBC_DATABASE_URL obbligatorio.");
    db = new Store(path,cfg);
    providers = provider;
    mailer = new Mailer(cfg);
    String host = SnapshotDatabase.setting(cfg,"BIND_HOST", "127.0.0.1");
    server = HttpServer.create(new InetSocketAddress(host, port), 32);
    publicBase = SnapshotDatabase.setting(cfg,"PUBLIC_BASE_URL", System.getenv().getOrDefault("RENDER_EXTERNAL_URL","http://127.0.0.1:" + server.getAddress().getPort())).replaceAll("/+$", "");
    server.createContext("/health",x->reply(x,200,Json.obj("ok",true,"storage",db.database==null?"local":"postgresql")));
    server.createContext("/api/", this::handle);
    server.createContext("/steam/callback", this::steamCallback);
    server.setExecutor(workers);
  }
  public void start() {
    server.start();
  }
  public int port() {
    return server.getAddress().getPort();
  }
  public void close() {
    server.stop(0);
    workers.shutdownNow();
  }
  static String s(Map < String, Object > m, String k) {
    return Json.str(m, k);
  }
  static List < Object > list(Object v) {
    return Json.list(v);
  }
  static Map < String, Object > map(Object v) {
    return Json.map(v);
  }
  String id() {
    byte [] b = new byte [24];
    random.nextBytes(b);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
  }
  static String hash(String password, byte [] salt) throws Exception {
    PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 210000, 256);
    try {
      return Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded());
    } finally {
      spec.clearPassword();
    }
  }
  Map < String, Object > safe(Map < String, Object > u) {
    return Json.obj("id", u.get("id"), "username", u.get("username"), "handle", u.get("handle"), "visibility", u.get("visibility"), "shareStats", u.get("shareStats"), "shareTrophies", u.get("shareTrophies"), "shareActivity", u.get("shareActivity"));
  }
  void reply(HttpExchange x, int status, Object data) throws IOException {
    byte [] b = Json.write(data).getBytes(StandardCharsets.UTF_8);
    x.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
    x.getResponseHeaders().set("Cache-Control", "no-store");
    x.sendResponseHeaders(status, b.length);
    try (OutputStream out = x.getResponseBody()) {
      out.write(b);
    }
  }
  void handle(HttpExchange x) throws IOException {
    try {
      if (! x.getRequestMethod().equals("POST")) {
        reply(x, 405, Json.obj("error", "Méthode non consentita"));
        return;
      }
      byte [] b = x.getRequestBody().readNBytes(262145);
      if (b.length > 262144) {
        reply(x, 413, Json.obj("error", "Richiesta troppo grande"));
        return;
      }
      Map < String, Object > input = map(Json.read(new String(b, StandardCharsets.UTF_8)));
      String route = x.getRequestURI().getPath().substring(5);
      String bearer = Objects.toString(x.getRequestHeaders().getFirst("Authorization"), "").replaceFirst("^Bearer ", "");
      Map < String, Object > result;
      synchronized (this) {
        result = dispatch(route, input, bearer, x.getRemoteAddress().getAddress().getHostAddress());
      }
      reply(x, 200, result);
    } catch (SecurityException e) {
      reply(x, 403, Json.obj("error", e.getMessage()));
    } catch (IllegalArgumentException | IllegalStateException e) {
      reply(x, 400, Json.obj("error", Objects.toString(e.getMessage(), "Richiesta non valida")));
    } catch (Exception e) {
      System.err.println("Richiesta fallita: " + e.getClass().getSimpleName());
      reply(x, 500, Json.obj("error", "Operazione non riuscita. Controlla il server o la connessione ai servizi esterni."));
    }
  }
  void rate(String key) {
    long now = System.currentTimeMillis();
    Deque < Long > a = attempts.computeIfAbsent(key, k -> new ArrayDeque < >());
    while (! a.isEmpty() && a.peek() < now - 60000) a.remove();
    if (a.size() >= 12) throw new IllegalArgumentException("Troppi tentativi: riprova tra un minuto.");
    a.add(now);
  }
  Map < String, Object > user(String token) {
    Session t = sessions.get(token);
    if (t == null || t.until < System.currentTimeMillis()) {
      sessions.remove(token);
      throw new SecurityException("Sessione scaduta. Accedi nuovamente.");
    }
    Map < String, Object > u = db.find("users", "id", t.user);
    if (u == null) throw new SecurityException("Account non disponibile");
    return u;
  }
  boolean friends(String a, String b) {
    return db.rows("requests").stream().map(Json::map).anyMatch(r -> s(r, "status").equals("accepted") &&((s(r, "from").equals(a) && s(r, "to").equals(b)) ||(s(r, "to").equals(a) && s(r, "from").equals(b))));
  }
  boolean visible(Map < String, Object > owner, String viewer) {
    return s(owner, "id").equals(viewer) || s(owner, "visibility").equals("public") ||(s(owner, "visibility").equals("friends") && friends(s(owner, "id"), viewer));
  }
  List < Object > ownedRows(String table, String uid) {
    List < Object > a = new ArrayList < >();
    for (Object o : db.rows(table)) if (s(map(o), "user").equals(uid)) a.add(o);
    return a;
  }
  Map < String, Object > entry(String uid, String gid) {
    for (Object o : db.rows("entries")) {
      Map < String, Object > e = map(o);
      if (s(e, "user").equals(uid) && s(e, "game").equals(gid)) return e;
    }
    return null;
  }
  void activity(String uid, String gid, String action) {
    db.rows("activities").add(Json.obj("user", uid, "game", gid, "action", action, "time", System.currentTimeMillis()));
    while (db.rows("activities").size() > 2000) db.rows("activities").remove(0);
  }
  Map < String, Object > dispatch(String route, Map < String, Object > p, String token, String ip) throws Exception {
    if (route.equals("health")) return Json.obj("ok", true, "igdb", providers.igdbReady(), "steam", providers.steamReady(), "version", "2.3", "mail", mailer.ready());
    if (route.equals("reset-request")) {
      rate(ip);
      if (! mailer.ready()) throw new IllegalStateException("Recupero password non disponibile: servizio email non configurato sul server.");
      String email = s(p, "email").trim().toLowerCase(Locale.ROOT);
      Map < String, Object > u = db.find("users", "email", email);
      resets.entrySet().removeIf(e -> e.getValue().until < System.currentTimeMillis());
      if (u != null) {
        String code = id();
        mailer.reset(email, code);
        resets.put(email, new Reset(s(u, "id"), code, System.currentTimeMillis() + 900000, 0));
      }
      return Json.obj("ok", true, "message", "Se l'indirizzo è registrato, riceverai un codice di recupero valido per 15 minuti.");
    }
    if (route.equals("reset-confirm")) {
      rate(ip);
      String email = s(p, "email").trim().toLowerCase(Locale.ROOT), pw = s(p, "password");
      Reset r = resets.get(email);
      if (r == null || r.until < System.currentTimeMillis() || r.tries >= 5) throw new IllegalArgumentException("Codice non valido o scaduto");
      resets.put(email, new Reset(r.user, r.code, r.until, r.tries + 1));
      if (! MessageDigest.isEqual(r.code.getBytes(StandardCharsets.UTF_8), s(p, "code").getBytes(StandardCharsets.UTF_8))) throw new IllegalArgumentException("Codice non valido o scaduto");
      if (pw.length() < 10 || pw.length() > 128) throw new IllegalArgumentException("Password: 10–128 caratteri");
      Map < String, Object > u = db.find("users", "id", r.user);
      byte [] salt = new byte [24];
      random.nextBytes(salt);
      u.put("salt", Base64.getEncoder().encodeToString(salt));
      u.put("hash", hash(pw, salt));
      sessions.entrySet().removeIf(e -> e.getValue().user.equals(r.user));
      resets.remove(email);
      db.save();
      return Json.obj("ok", true);
    }
    if (route.equals("register") || route.equals("login")) {
      rate(ip);
      String email = s(p, "email").trim().toLowerCase(Locale.ROOT), password = s(p, "password");
      if (route.equals("register")) {
        String name = s(p, "username").trim();
        if (! name.matches("[\\p{L}0-9_ -]{3,24}")) throw new IllegalArgumentException("Nome utente: 3–24 lettere, numeri, spazi, _ o -.");
        if (! email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || email.length() > 254) throw new IllegalArgumentException("Email non valida");
        if (password.length() < 10 || password.length() > 128) throw new IllegalArgumentException("Password: da 10 a 128 caratteri.");
        if (db.find("users", "email", email) != null) throw new IllegalArgumentException("Impossibile registrare questo indirizzo.");
        byte [] salt = new byte [24];
        random.nextBytes(salt);
        String uid = id(), handle;
        do {
          handle = name + "#" + String.format("%06d", random.nextInt(1000000));
        }
        while (db.find("users", "handle", handle) != null);
        Map < String, Object > u = Json.obj("id", uid, "username", name, "handle", handle, "email", email, "salt", Base64.getEncoder().encodeToString(salt), "hash", hash(password, salt), "visibility", "private", "shareStats", false, "shareTrophies", false, "shareActivity", false, "created", System.currentTimeMillis());
        db.rows("users").add(u);
        db.save();
        return signIn(u);
      }
      Map < String, Object > u = db.find("users", "email", email);
      String computed = hash(password, u == null ? new byte [24] : Base64.getDecoder().decode(s(u, "salt")));
      if (u == null || ! MessageDigest.isEqual(computed.getBytes(StandardCharsets.UTF_8), s(u, "hash").getBytes(StandardCharsets.UTF_8))) throw new SecurityException("Email o password non corrette");
      return signIn(u);
    }
    Map < String, Object > me = user(token);
    String uid = s(me, "id");
    switch (route) {
      case "logout" : sessions.remove(token);
      return Json.obj("ok", true);
      case "state" : {
        List < Object > fr = new ArrayList < >(), requests = new ArrayList < >(), feed = new ArrayList < >();
        for (Object o : db.rows("requests")) {
          Map < String, Object > r = map(o);
          if (s(r, "from").equals(uid) || s(r, "to").equals(uid)) {
            String other = s(r, "from").equals(uid) ? s(r, "to") : s(r, "from");
            Map < String, Object > u = db.find("users", "id", other);
            if (u == null) continue;
            requests.add(Json.obj("id", r.get("id"), "status", r.get("status"), "incoming", s(r, "to").equals(uid), "other", safe(u)));
            if (s(r, "status").equals("accepted")) fr.add(safe(u));
          }
        }
        for (Object o : db.rows("activities")) {
          Map < String, Object > a = map(o), u = db.find("users", "id", s(a, "user"));
          if (u != null && friends(uid, s(u, "id")) && Json.yes(u, "shareActivity") && visible(u, uid)) {
            Map < String, Object > v = new LinkedHashMap < >(a);
            v.put("handle", u.get("handle"));
            feed.add(v);
          }
        }
        Collections.reverse(feed);
        Map < String, Object > profile = safe(me);
        profile.put("email", me.get("email"));
        return Json.obj("me", profile, "games", db.rows("games"), "entries", ownedRows("entries", uid), "friends", fr, "requests", requests, "feed", feed, "links", ownedRows("links", uid), "lists", db.rows("lists").stream().filter(o -> s(map(o), "user").equals(uid) || Json.yes(map(o), "public")).toList(), "igdb", providers.igdbReady(), "steam", providers.steamReady());
      }
      case "catalog" : {
        List < Object > g = providers.catalog(s(p, "query"), s(p, "mode"), (int) Math.max(0, Math.min(10000, Json.num(p, "offset"))));
        for (Object o : g) {
          Map < String, Object > v = map(o);
          if (db.find("games", "id", s(v, "id")) == null) db.rows("games").add(v);
        }
        db.save();
        return Json.obj("games", g);
      }
      case "entry" : {
        String gid = s(p, "game");
        if (db.find("games", "id", gid) == null) throw new IllegalArgumentException("Gioco non trovato");
        Map < String, Object > e = entry(uid, gid);
        String status = s(p, "status");
        if (! Set.of("", "FINITO", "IN_CORSO", "DA_GIOCARE", "DESIDERATO").contains(status)) throw new IllegalArgumentException("Stato non valido");
        Object rating = p.get("rating");
        if (rating != null &&(!(rating instanceof Number) || ! Double.isFinite(((Number) rating).doubleValue()) ||((Number) rating).doubleValue() < 0 ||((Number) rating).doubleValue() > 10)) throw new IllegalArgumentException("Voto da 0 a 10");
        if (e == null) {
          e = Json.obj("user", uid, "game", gid, "status", "", "favorite", false, "rating", null);
          db.rows("entries").add(e);
        }
        String before = s(e, "status");
        e.put("status", status);
        e.put("favorite", Json.yes(p, "favorite"));
        e.put("rating", rating);
        e.put("updated", System.currentTimeMillis());
        for (Object o : db.rows("reviews")) {
          Map < String, Object > r = map(o);
          if (s(r, "user").equals(uid) && s(r, "game").equals(gid)) r.put("rating", rating);
        }
        if (! before.equals(status) && ! status.isBlank()) activity(uid, gid, status.equals("FINITO") ? "ha completato" : "ha aggiornato lo stato di");
        if (status.isBlank() && ! Json.yes(e, "favorite") && rating == null) db.rows("entries").remove(e);
        db.save();
        return Json.obj("ok", true);
      }
      case "reviews" : {
        List < Object > out = new ArrayList < >();
        for (Object o : db.rows("reviews")) {
          Map < String, Object > r = map(o);
          if (s(r, "game").equals(s(p, "game"))) {
            Map < String, Object > v = new LinkedHashMap < >(r), u = db.find("users", "id", s(r, "user"));
            v.put("handle", u == null ? "Account eliminato" : u.get("handle"));
            Map < String, Object > votes = map(r.get("votes"));
            v.remove("votes");
            v.put("likes", votes.values().stream().filter(x -> x instanceof Number &&((Number) x).intValue() == 1).count());
            v.put("dislikes", votes.values().stream().filter(x -> x instanceof Number &&((Number) x).intValue() == - 1).count());
            v.put("myVote", votes.getOrDefault(uid, 0));
            out.add(v);
          }
        }
        return Json.obj("reviews", out);
      }
      case "review" : {
        String gid = s(p, "game"), text = s(p, "text").trim();
        if (db.find("games", "id", gid) == null) throw new IllegalArgumentException("Gioco non trovato");
        if (text.length() > 10000) throw new IllegalArgumentException("Commento troppo lungo (massimo tecnico 10.000 caratteri)");
        Map < String, Object > r = null;
        for (Object o : db.rows("reviews")) if (s(map(o), "user").equals(uid) && s(map(o), "game").equals(gid)) r = map(o);
        if (text.isEmpty()) {
          if (r != null) db.rows("reviews").remove(r);
        } else {
          if (r == null) {
            r = Json.obj("id", id(), "user", uid, "game", gid, "votes", Json.obj());
            db.rows("reviews").add(r);
          }
          Map < String, Object > e = entry(uid, gid);
          r.put("text", text);
          r.put("rating", e == null ? null : e.get("rating"));
          r.put("updated", System.currentTimeMillis());
        }
        db.save();
        return Json.obj("ok", true);
      }
      case "vote" : {
        Map < String, Object > r = db.find("reviews", "id", s(p, "review"));
        int v =(int) Json.num(p, "value");
        if (r == null || ! Set.of(- 1, 0, 1).contains(v)) throw new IllegalArgumentException("Voto non valido");
        if (s(r, "user").equals(uid)) throw new IllegalArgumentException("Non puoi votare la tua recensione");
        Map < String, Object > vs = map(r.get("votes"));
        if (v == 0) vs.remove(uid);
        else vs.put(uid, v);
        db.save();
        return Json.obj("ok", true);
      }
      case "find-user" : {
        Map < String, Object > u = db.find("users", "handle", s(p, "handle").trim());
        return Json.obj("user", u == null ? null : safe(u));
      }
      case "friend" : {
        String to = s(p, "user");
        if (to.equals(uid) || db.find("users", "id", to) == null) throw new IllegalArgumentException("Utente non valido");
        for (Object o : db.rows("requests")) {
          Map < String, Object > r = map(o);
          if ((s(r, "from").equals(uid) && s(r, "to").equals(to)) ||(s(r, "to").equals(uid) && s(r, "from").equals(to))) throw new IllegalArgumentException("Amicizia o richiesta già presente");
        }
        db.rows("requests").add(Json.obj("id", id(), "from", uid, "to", to, "status", "pending"));
        db.save();
        return Json.obj("ok", true);
      }
      case "friend-action" : {
        Map < String, Object > r = db.find("requests", "id", s(p, "request"));
        if (r == null) throw new IllegalArgumentException("Richiesta non trovata");
        String action = s(p, "action");
        if (action.equals("accept")) {
          if (! s(r, "to").equals(uid) || ! s(r, "status").equals("pending")) throw new SecurityException("Richiesta non disponibile");
          r.put("status", "accepted");
        } else {
          if (! s(r, "to").equals(uid) && ! s(r, "from").equals(uid)) throw new SecurityException("Non consentito");
          db.rows("requests").remove(r);
        }
        db.save();
        return Json.obj("ok", true);
      }
      case "profile" : {
        Map < String, Object > u = db.find("users", "id", s(p, "user"));
        if (u == null) throw new IllegalArgumentException("Utente non trovato");
        boolean self = s(u, "id").equals(uid), access = visible(u, uid);
        return Json.obj("user", safe(u), "visible", access, "entries", access ? ownedRows("entries", s(u, "id")) : List.of(), "links", access &&(self || Json.yes(u, "shareTrophies") || Json.yes(u, "shareStats")) ? sharedLinks(u, self) : List.of(), "statsAllowed", access &&(self || Json.yes(u, "shareStats")), "trophiesAllowed", access &&(self || Json.yes(u, "shareTrophies")));
      }
      case "profile-update" : {
        String name = s(p, "username").trim();
        if (! name.matches("[\\p{L}0-9_ -]{3,24}")) throw new IllegalArgumentException("Nome utente: 3–24 lettere, numeri, spazi, _ o -.");
        String suffix = s(me, "handle").substring(s(me, "handle").lastIndexOf('#'));
        String handle = name + suffix;
        Map < String, Object > exists = db.find("users", "handle", handle);
        if (exists != null && ! s(exists, "id").equals(uid)) throw new IllegalArgumentException("Identificativo già utilizzato");
        me.put("username", name);
        me.put("handle", handle);
        for (Object o : ownedRows("lists", uid)) map(o).put("handle", handle);
        db.save();
        return Json.obj("ok", true);
      }
      case "settings" : {
        String vis = s(p, "visibility");
        if (! Set.of("private", "friends", "public").contains(vis)) throw new IllegalArgumentException("Privacy non valida");
        me.put("visibility", vis);
        for (String k : List.of("shareStats", "shareTrophies", "shareActivity")) me.put(k, Json.yes(p, k));
        db.save();
        return Json.obj("ok", true);
      }
      case "password" : {
        if (! MessageDigest.isEqual(hash(s(p, "old"), Base64.getDecoder().decode(s(me, "salt"))).getBytes(StandardCharsets.UTF_8), s(me, "hash").getBytes(StandardCharsets.UTF_8))) throw new SecurityException("Password attuale errata");
        String pw = s(p, "new");
        if (pw.length() < 10 || pw.length() > 128) throw new IllegalArgumentException("Password: 10–128 caratteri");
        byte [] salt = new byte [24];
        random.nextBytes(salt);
        me.put("salt", Base64.getEncoder().encodeToString(salt));
        me.put("hash", hash(pw, salt));
        sessions.entrySet().removeIf(e -> e.getValue().user.equals(uid) && ! e.getKey().equals(token));
        db.save();
        return Json.obj("ok", true);
      }
      case "export" : return Json.obj("profile", safe(me), "entries", ownedRows("entries", uid), "reviews", ownedRows("reviews", uid).stream().map(o -> {
        Map < String, Object > r = new LinkedHashMap < >(map(o));
        r.remove("votes");
        return r;
      }).toList(), "links", ownedRows("links", uid), "lists", ownedRows("lists", uid));
      case "list-create" : {
        String title = s(p, "title").trim();
        if (title.length() < 1 || title.length() > 100) throw new IllegalArgumentException("Titolo: 1–100 caratteri");
        List < Object > ids = list(p.get("games"));
        if (ids.size() > 100) throw new IllegalArgumentException("Massimo 100 giochi per lista");
        for (Object g : ids) if (db.find("games", "id", g.toString()) == null) throw new IllegalArgumentException("Gioco non trovato");
        db.rows("lists").add(Json.obj("id", id(), "user", uid, "handle", me.get("handle"), "title", title, "games", ids, "public", Json.yes(p, "public")));
        db.save();
        return Json.obj("ok", true);
      }
      case "list-delete" : {
        Map < String, Object > l = db.find("lists", "id", s(p, "id"));
        if (l == null || ! s(l, "user").equals(uid)) throw new SecurityException("Non consentito");
        db.rows("lists").remove(l);
        db.save();
        return Json.obj("ok", true);
      }
      case "steam-start" : {
        if (! providers.steamReady()) throw new IllegalStateException("Configura STEAM_API_KEY sul server prima del collegamento.");
        pending.entrySet().removeIf(e -> e.getValue().until < System.currentTimeMillis());
        String state = id(), callback = publicBase + "/steam/callback?state=" + state;
        pending.put(state, new Pending(uid, System.currentTimeMillis() + 600000, callback));
        Map < String, String > q = new LinkedHashMap < >();
        q.put("openid.ns", "http://specs.openid.net/auth/2.0");
        q.put("openid.mode", "checkid_setup");
        q.put("openid.return_to", callback);
        q.put("openid.realm", publicBase + "/");
        q.put("openid.identity", "http://specs.openid.net/auth/2.0/identifier_select");
        q.put("openid.claimed_id", "http://specs.openid.net/auth/2.0/identifier_select");
        return Json.obj("url", "https://steamcommunity.com/openid/login?" + form(q));
      }
      case "steam-unlink" : db.rows("links").removeIf(o -> s(map(o), "user").equals(uid));
      db.rows("snapshots").removeIf(o -> s(map(o), "user").equals(uid));
      db.save();
      return Json.obj("ok", true);
      case "steam-sync" : return syncSteam(uid);
      case "achievements" : {
        String target = s(p, "user");
        if (target.isEmpty()) target = uid;
        Map < String, Object > u = db.find("users", "id", target);
        if (u == null ||(! target.equals(uid) &&(! visible(u, uid) || ! Json.yes(u, "shareTrophies")))) throw new SecurityException("Trofei non condivisi");
        Map < String, Object > link = db.find("links", "user", target);
        if (link == null) throw new IllegalArgumentException("Nessun account Steam collegato");
        String app = s(p, "app");
        List < Object > a = providers.achievements(s(link, "steamId"), app);
        if (target.equals(uid)) {
          Map < String, Object > cache = map(link.computeIfAbsent("trophies", k -> Json.obj()));
          cache.put(app, a);
          link.put("trophiesSyncedAt", System.currentTimeMillis());
          db.save();
        }
        return Json.obj("achievements", a);
      }
      case "ranking" : {
        String month = YearMonth.now(ZoneOffset.UTC).toString();
        Map < String, Long > totals = new HashMap < >();
        for (Object o : db.rows("snapshots")) {
          Map < String, Object > v = map(o), u = db.find("users", "id", s(v, "user"));
          if (s(v, "month").equals(month) && u != null && Json.yes(u, "shareStats") && s(u, "visibility").equals("public")) totals.merge(s(v, "game"), Math.max(0, Json.num(v, "last") - Json.num(v, "baseline")), Long::sum);
        }
        List < Object > rank = new ArrayList < >();
        totals.entrySet().stream().sorted(Map.Entry.< String, Long > comparingByValue().reversed()).forEach(e -> rank.add(Json.obj("game", e.getKey(), "minutes", e.getValue())));
        return Json.obj("month", month, "ranking", rank, "note", "Variazioni osservate dalle sincronizzazioni: non storico mensile completo. Solo utenti con " + "statistiche pubbliche.");
      }
      default : throw new IllegalArgumentException("Operazione non riconosciuta");
    }
  }
  List < Object > sharedLinks(Map < String, Object > owner, boolean self) {
    List < Object > out = new ArrayList < >();
    for (Object o : ownedRows("links", s(owner, "id"))) {
      Map < String, Object > v = new LinkedHashMap < >(map(o));
      v.remove("steamId");
      if (! self && ! Json.yes(owner, "shareStats")) {
        List < Object > games = new ArrayList < >();
        for (Object g : list(v.get("games"))) games.add(Json.obj("game", map(g).get("game")));
        v.put("games", games);
      }
      if (! self && ! Json.yes(owner, "shareTrophies")) {
        v.remove("trophies");
        v.remove("trophiesSyncedAt");
      }
      out.add(v);
    }
    return out;
  }
  Map < String, Object > signIn(Map < String, Object > u) {
    sessions.entrySet().removeIf(e -> e.getValue().until < System.currentTimeMillis());
    String t = id();
    sessions.put(t, new Session(s(u, "id"), System.currentTimeMillis() + 86400000));
    return Json.obj("token", t, "user", safe(u));
  }
  Map < String, Object > syncSteam(String uid) throws Exception {
    Map < String, Object > link = db.find("links", "user", uid);
    if (link == null) throw new IllegalArgumentException("Collega prima Steam");
    List < Object > remote = providers.steamGames(s(link, "steamId"));
    long now = System.currentTimeMillis();
    String month = YearMonth.now(ZoneOffset.UTC).toString();
    List < Object > imported = new ArrayList < >();
    for (Object o : remote) {
      Map < String, Object > g = map(o);
      String app = "" + Json.num(g, "appid"), gid = "steam:" + app;
      long minutes = Math.max(0, Json.num(g, "playtime_forever"));
      if (db.find("games", "id", gid) == null) db.rows("games").add(Json.obj("id", gid, "steamApp", app, "name", s(g, "name"), "platforms", "Steam / PC", "genres", "", "cover", "https://cdn.akamai.steamstatic.com/steam/apps/" + app + "/library_600x900.jpg", "hero", "https://cdn.akamai.steamstatic.com/steam/apps/" + app + "/library_hero.jpg", "summary", "Gioco importato dal tuo account Steam.", "source", "Steam"));
      Map < String, Object > snap = null;
      for (Object v : db.rows("snapshots")) {
        Map < String, Object > x = map(v);
        if (s(x, "user").equals(uid) && s(x, "game").equals(gid) && s(x, "month").equals(month)) snap = x;
      }
      if (snap == null) {
        snap = Json.obj("user", uid, "game", gid, "month", month, "baseline", minutes, "firstAt", now);
        db.rows("snapshots").add(snap);
      }
      if (minutes < Json.num(snap, "last")) {
        snap.put("baseline", minutes);
        snap.put("reset", true);
      }
      snap.put("last", minutes);
      snap.put("updated", now);
      imported.add(Json.obj("game", gid, "minutes", minutes));
    }
    link.put("games", imported);
    link.put("syncedAt", now);
    db.save();
    return Json.obj("count", imported.size(), "ok", true);
  }
  static String form(Map < String, String > m) {
    StringJoiner j = new StringJoiner("&");
    m.forEach((k, v) -> j.add(Providers.enc(k) + "=" + Providers.enc(v)));
    return j.toString();
  }
  static Map < String, String > query(String q) {
    Map < String, String > m = new LinkedHashMap < >();
    if (q != null) for (String pair : q.split("&")) {
      String [] a = pair.split("=", 2);
      m.put(URLDecoder.decode(a [0], StandardCharsets.UTF_8), a.length == 2 ? URLDecoder.decode(a [1], StandardCharsets.UTF_8) : "");
    }
    return m;
  }
  void steamCallback(HttpExchange x) throws IOException {
    String text;
    int status = 200;
    try {
      synchronized (this) {
        Map < String, String > q = query(x.getRequestURI().getRawQuery());
        Pending p = pending.remove(q.get("state"));
        if (p == null || p.until < System.currentTimeMillis()) throw new SecurityException("Collegamento scaduto");
        if (! "id_res".equals(q.get("openid.mode")) || ! p.callback.equals(q.get("openid.return_to")) || ! "https://steamcommunity.com/openid/login".equals(q.get("openid.op_endpoint"))) throw new SecurityException("Risposta Steam non valida");
        String claimed = q.getOrDefault("openid.claimed_id", "");
        if (! claimed.matches("https://steamcommunity.com/openid/id/[0-9]{17}") || ! claimed.equals(q.get("openid.identity"))) throw new SecurityException("Identità Steam non valida");
        String signed = q.getOrDefault("openid.signed", "");
        for (String k : List.of("op_endpoint", "claimed_id", "identity", "return_to", "response_nonce", "assoc_handle")) if (! Arrays.asList(signed.split(",")).contains(k)) throw new SecurityException("Firma Steam incompleta");
        String nonce = q.getOrDefault("openid.response_nonce", "");
        if (nonce.length() < 20 || Math.abs(Duration.between(Instant.parse(nonce.substring(0, 20)), Instant.now()).toSeconds()) > 600) throw new SecurityException("Risposta Steam scaduta");
        q.remove("state");
        q.put("openid.mode", "check_authentication");
        HttpResponse < String > r = providers.http.send(HttpRequest.newBuilder(URI.create("https://steamcommunity.com/openid/login")).timeout(Duration.ofSeconds(20)).header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form(q))).build(), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200 || ! r.body().lines().anyMatch(v -> v.trim().equals("is_valid:true"))) throw new SecurityException("Verifica Steam fallita");
        String sid = claimed.substring(claimed.lastIndexOf('/') + 1);
        for (Object o : db.rows("links")) if (s(map(o), "steamId").equals(sid) && ! s(map(o), "user").equals(p.user)) throw new IllegalArgumentException("Account Steam già associato");
        db.rows("links").removeIf(o -> s(map(o), "user").equals(p.user));
        db.rows("snapshots").removeIf(o -> s(map(o), "user").equals(p.user));
        db.rows("links").add(Json.obj("user", p.user, "platform", "Steam", "steamId", sid, "games", new ArrayList < >()));
        db.save();
        text = "Steam collegato. Puoi tornare nell'app, aggiornare e sincronizzare.";
      }
    } catch (Exception e) {
      status = 400;
      text = "Collegamento non riuscito. Torna nell'app e riprova.";
    }
    byte [] b = text.getBytes(StandardCharsets.UTF_8);
    x.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
    x.getResponseHeaders().set("Cache-Control", "no-store");
    x.sendResponseHeaders(status, b.length);
    try (OutputStream out = x.getResponseBody()) {
      out.write(b);
    }
  }
  public static void main(String [] args) throws Exception {
    Properties cfg = new Properties();
    Path conf = Path.of("server.properties");
    if (Files.exists(conf)) try (Reader r = Files.newBufferedReader(conf)) {
      cfg.load(r);
    }
    Path data = Path.of(cfg.getProperty("DATA_FILE", "data/database.json"));
    Server s = new Server(data, Integer.parseInt(SnapshotDatabase.setting(cfg,"PORT", "8787")), cfg);
    s.start();
    Runtime.getRuntime().addShutdownHook(new Thread(s::close));
    System.out.println("GameBackLog server: " + s.publicBase + " (dati: " + data.toAbsolutePath() + ")");
  }
}
