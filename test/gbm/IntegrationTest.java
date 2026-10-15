package gbm;
import java.nio.file.*;
import java.util.*;
import java.net.*;
import java.net.http.*;
public class IntegrationTest {
  static int checks = 0;
  static void check(boolean x, String name) {
    if (! x) throw new AssertionError(name);
    checks ++;
    System.out.println("OK " + name);
  }
  static Map < String, Object > obj(Object...x) {
    return Json.obj(x);
  }
  static String s(Map < String, Object > m, String k) {
    return Json.str(m, k);
  }
  static void denied(Api api, String route, Map < String, Object > args, String message) throws Exception {
    try {
      api.call(route, args);
      throw new AssertionError(message);
    } catch (IllegalStateException expected) {
      check(true, message);
    }
  }
  public static void main(String [] args) throws Exception {
    Path dir = Files.createTempDirectory("gbm-test");
    Properties cfg = new Properties();
    cfg.setProperty("GBM_TEST_ISOLATED","true");
    try (Server server = new Server(dir.resolve("db.json"), 0, cfg)) {
      server.start();
      String base = "http://127.0.0.1:" + server.port();
      Api a = new Api(base), b = new Api(base), anon = new Api(base);
      check(Json.yes(a.call("health", obj()), "ok"), "server health");
      denied(anon, "state", obj(), "anonymous access rejected");
      Map < String, Object > ar = a.call("register", obj("email", "renato@example.com", "username", "renato", "password", "Password-di-test-01"));
      a.token = s(ar, "token");
      String aid = s(Json.map(ar.get("user")), "id");
      Map < String, Object > br = b.call("register", obj("email", "luna@example.com", "username", "LunaPixel", "password", "Password-di-test-02"));
      b.token = s(br, "token");
      String bid = s(Json.map(br.get("user")), "id"), handle = s(Json.map(br.get("user")), "handle");
      check(! aid.equals(bid), "unique user IDs");
      check(s(Json.map(br.get("user")), "visibility").equals("private"), "private by default");
      check(! Json.write(ar).contains("hash"), "password hash never sent");
      String game = "local:minecraft";
      a.call("entry", obj("game", game, "status", "", "favorite", true, "rating", null));
      Map < String, Object > st = a.call("state", obj());
      Map < String, Object > en = Json.map(Json.list(st.get("entries")).get(0));
      check(Json.yes(en, "favorite") && s(en, "status").isEmpty(), "favorite independent of library");
      denied(a, "entry", obj("game", game, "status", "IN_CORSO", "rating", 11), "invalid rating rejected");
      a.call("entry", obj("game", game, "status", "FINITO", "favorite", true, "rating", 8.5));
      a.call("review", obj("game", game, "text", "Recensione con accenti: è utile <script>test</script>"));
      Map < String, Object > review = Json.map(Json.list(a.call("reviews", obj("game", game)).get("reviews")).get(0));
      String rid = s(review, "id");
      denied(a, "vote", obj("review", rid, "value", 1), "own review vote rejected");
      b.call("vote", obj("review", rid, "value", 1));
      b.call("vote", obj("review", rid, "value", - 1));
      review = Json.map(Json.list(a.call("reviews", obj("game", game)).get("reviews")).get(0));
      check(Json.num(review, "likes") == 0 && Json.num(review, "dislikes") == 1, "single vote per user and replacement");
      check(! review.containsKey("votes"), "voter identities not leaked");
      Map < String, Object > profile = b.call("profile", obj("user", aid));
      check(! Json.yes(profile, "visible") && Json.list(profile.get("entries")).isEmpty(), "private library hidden server-side");
      a.call("friend", obj("user", bid));
      Map < String, Object > req = Json.map(Json.list(b.call("state", obj()).get("requests")).get(0));
      denied(a, "friend-action", obj("request", req.get("id"), "action", "accept"), "sender cannot accept own request");
      b.call("friend-action", obj("request", req.get("id"), "action", "accept"));
      check(Json.list(a.call("state", obj()).get("friends")).size() == 1, "friend acceptance");
      a.call("settings", obj("visibility", "friends", "shareStats", true, "shareTrophies", false, "shareActivity", true));
      profile = b.call("profile", obj("user", aid));
      check(Json.yes(profile, "visible") && Json.yes(profile, "statsAllowed"), "friend-only library and comparison permitted");
      // Fixture only: test provider-neutral privacy without making network calls.
      synchronized (server) {
        server.db.rows("links").add(obj("user", aid, "steamId", "12345678901234567", "games", List.of(obj("game", "steam:10", "minutes", 999)), "trophies", obj("10", List.of(obj("id", "ach", "unlocked", true)))));
        server.db.save();
      }
      a.call("settings", obj("visibility", "friends", "shareStats", false, "shareTrophies", true, "shareActivity", false));
      profile = b.call("profile", obj("user", aid));
      String serialized = Json.write(profile);
      check(! serialized.contains("999") && ! serialized.contains("12345678901234567"), "trophy sharing does not leak hours or provider ID");
      a.call("settings", obj("visibility", "friends", "shareStats", true, "shareTrophies", false, "shareActivity", false));
      profile = b.call("profile", obj("user", aid));
      check(! Json.write(profile).contains("\"ach\""), "stats sharing does not leak trophies");
      denied(b, "achievements", obj("user", aid, "app", "10"), "private trophies blocked");
      a.call("list-create", obj("title", "Privata", "games", List.of(game), "public", false));
      check(Json.list(b.call("state", obj()).get("lists")).isEmpty(), "private list hidden");
      a.call("list-create", obj("title", "Pubblica", "games", List.of(game), "public", true));
      check(Json.list(b.call("state", obj()).get("lists")).size() == 1, "public list visible");
      denied(a, "catalog", obj("query", "Mario"), "unconfigured provider explicit error");
      denied(a, "steam-start", obj(), "unconfigured Steam explicit error");
      a.call("password", obj("old", "Password-di-test-01", "new", "Password-nuova-test-03"));
      a.call("logout", obj());
      denied(a, "state", obj(), "logout revokes token");
      denied(a, "login", obj("email", "renato@example.com", "password", "Password-di-test-01"), "old password invalidated");
      ar = a.call("login", obj("email", "renato@example.com", "password", "Password-nuova-test-03"));
      a.token = s(ar, "token");
      check(! Json.write(a.call("export", obj())).contains("hash"), "export excludes credentials");
      Store restored = new Store(dir.resolve("db.json"));
      check(restored.rows("users").size() == 2 && restored.rows("reviews").size() == 1, "persistent store reload");
      check(! Files.readString(dir.resolve("db.json")).contains("Password-nuova-test-03"), "password not stored in plaintext");
      HttpResponse < String > callback = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(base + "/steam/callback?state=bogus")).GET().build(), HttpResponse.BodyHandlers.ofString());
      check(callback.statusCode() == 400, "invalid OpenID callback rejected");
      denied(anon, "reset-request", obj("email", "renato@example.com"), "unconfigured SMTP explicit error");
      synchronized (server) {
        server.resets.put("renato@example.com", new Server.Reset(aid, "test-reset-code", System.currentTimeMillis() + 60000, 0));
      }
      denied(anon, "reset-confirm", obj("email", "renato@example.com", "code", "wrong", "password", "another-password-04"), "wrong reset code rejected");
      anon.call("reset-confirm", obj("email", "renato@example.com", "code", "test-reset-code", "password", "another-password-04"));
      denied(a, "state", obj(), "password reset revokes sessions");
      denied(anon, "reset-confirm", obj("email", "renato@example.com", "code", "test-reset-code", "password", "another-password-05"), "reset code single use");
      Map < String, Object > v = obj("text", "è \"ciao\"\n\\", "a", List.of(1, true));
      check(Json.write(Json.read(Json.write(v))).equals(Json.write(v)), "JSON roundtrip");
      System.out.println("PASS " + checks + " checks");
    }
  }
}
