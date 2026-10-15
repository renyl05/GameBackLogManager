package gbm;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;
import java.nio.file.*;
public class ProviderTest {
  static void ok(boolean v, String s) {
    IntegrationTest.check(v, s);
  }
  public static void main(String [] args) throws Exception {
    Properties cfg = new Properties();
    cfg.setProperty("GBM_TEST_ISOLATED","true");
    cfg.setProperty("IGDB_CLIENT_ID", "test-id");
    cfg.setProperty("IGDB_CLIENT_SECRET", "test-secret");
    cfg.setProperty("STEAM_API_KEY", "test-steam-key");
    Fake f = new Fake();
    Providers p = new Providers(cfg, f);
    List < Object > games = p.catalog("Mario \"test\"", "retro", 36);
    Map < String, Object > game = Json.map(games.get(0));
    ok(Json.str(game, "id").equals("igdb:42"), "IGDB stable ID");
    ok(Json.str(game, "platforms").equals("Super Nintendo"), "retro platform metadata");
    ok(Json.str(game, "cover").contains("t_cover_big"), "IGDB image URL");
    ok(f.igdbRequest.contains("offset 36;") && f.igdbRequest.contains("platforms = (") && f.igdbRequest.contains("\\\"test\\\""), "IGDB query escaping, paging and retro filter");
    ok(p.steamGames("123").size() == 1, "Steam library response");
    List < Object > a = p.achievements("123", "10");
    ok(Json.yes(Json.map(a.get(0)), "unlocked") && Json.str(Json.map(a.get(0)), "name").equals("Primo passo"), "Steam schema and player achievements merged");
    Properties mail=new Properties();mail.setProperty("GBM_TEST_ISOLATED","true");mail.setProperty("BREVO_API_KEY","test-brevo");mail.setProperty("SMTP_FROM","sender@example.com");
    Mailer mailer=new Mailer(mail,f);ok(mailer.ready(),"HTTPS email provider configured without SMTP");mailer.reset("recipient@example.com","test-code");
    Path dir = Files.createTempDirectory("gbm-providers");
    try (Server server = new Server(dir.resolve("db.json"), 0, cfg, p)) {
      server.start();
      Api api = new Api("http://127.0.0.1:" + server.port());
      Map < String, Object > u = api.call("register", Json.obj("username", "tester", "email", "test@example.com", "password", "provider-test-123"));
      api.token = Json.str(u, "token");
      String uid = Json.str(Json.map(u.get("user")), "id");
      synchronized (server) {
        server.db.rows("links").add(Json.obj("user", uid, "platform", "Steam", "steamId", "123", "games", List.of()));
      }
      api.call("steam-sync", Json.obj());
      Map < String, Object > snap = Json.map(server.db.rows("snapshots").get(0));
      ok(Json.num(snap, "baseline") == 120 && Json.num(snap, "last") == 120, "first sync excludes historical hours");
      f.minutes = 180;
      api.call("steam-sync", Json.obj());
      ok(Json.num(snap, "last") - Json.num(snap, "baseline") == 60, "subsequent sync measures observed delta");
      api.call("achievements", Json.obj("app", "10"));
      ok(! Json.map(Json.map(server.db.rows("links").get(0)).get("trophies")).isEmpty(), "achievement cache persisted");
      f.minutes = 60;
      api.call("steam-sync", Json.obj());
      ok(Json.num(snap, "last") == 60 && Json.num(snap, "baseline") == 60 && Json.yes(snap, "reset"), "provider counter reset does not create negative hours");
      api.call("steam-unlink", Json.obj());
      ok(server.db.rows("links").isEmpty() && server.db.rows("snapshots").isEmpty(), "unlink removes imported data and observations");
    }
    System.out.println("PASS provider fixture tests (no live credentials used)");
  }
  static class Fake extends HttpClient {
    int minutes = 120;
    String igdbRequest = "";
    public < T > HttpResponse < T > send(HttpRequest req, HttpResponse.BodyHandler < T > handler) throws java.io.IOException, InterruptedException {
      String path = req.uri().getPath(), body;
      if (path.contains("oauth2/token")) body = "{\"access_token\":\"mock-token\",\"expires_in\":3600}";
      else if (path.equals("/v4/games")) {
        ok(req.headers().firstValue("Authorization").orElse("").equals("Bearer mock-token"), "IGDB bearer authentication");
        StringBuilder data = new StringBuilder();
        req.bodyPublisher().get().subscribe(new java.util.concurrent.Flow.Subscriber < java.nio.ByteBuffer >() {
          public void onSubscribe(java.util.concurrent.Flow.Subscription s) {
            s.request(Long.MAX_VALUE);
          }
          public void onNext(java.nio.ByteBuffer b) {
            byte [] v = new byte [b.remaining()];
            b.get(v);
            data.append(new String(v, java.nio.charset.StandardCharsets.UTF_8));
          }
          public void onError(Throwable t) {
          }
          public void onComplete() {
          }
        });
        igdbRequest = data.toString();
        body = "[{\"id\":42,\"name\":\"Test " + "Game\",\"cover\":{\"image_id\":\"cover42\"},\"platforms\":[{\"name\":\"Super " + "Nintendo\"}],\"genres\":[{\"name\":\"Platform\"}]}]";
      } else if (path.contains("GetOwnedGames")) body = "{\"response\":{\"games\":[{\"appid\":10,\"name\":\"Test Game\",\"playtime_forever\":" + minutes + "}]}}";
      else if (path.contains("GetPlayerAchievements")) body = "{\"playerstats\":{\"success\":true,\"achievements\":[{\"apiname\":\"FIRST\"," + "\"achieved\":1,\"unlocktime\":123}]}}";
      else if (path.contains("GetSchemaForGame")) body = "{\"game\":{\"availableGameStats\":{\"achievements\":[{\"name\":\"FIRST\"," + "\"displayName\":\"Primo passo\",\"description\":\"Inizia\"}]}}}";
      else if(path.equals("/v3/smtp/email")){ok(req.headers().firstValue("api-key").orElse("").equals("test-brevo"),"Brevo API key header");body="{\"messageId\":\"fixture\"}";}
      else throw new java.io.IOException("Unexpected test URL");
      final String answer = body;
      return new HttpResponse < T >() {
        public int statusCode() {
          return 200;
        }
        public HttpRequest request() {
          return req;
        }
        public Optional < HttpResponse < T >> previousResponse() {
          return Optional.empty();
        }
        public HttpHeaders headers() {
          return HttpHeaders.of(Map.of(), (a, b) -> true);
        }
        @SuppressWarnings("unchecked") public T body() {
          return(T) answer;
        }
        public Optional < SSLSession > sslSession() {
          return Optional.empty();
        }
        public URI uri() {
          return req.uri();
        }
        public HttpClient.Version version() {
          return Version.HTTP_1_1;
        }
      };
    }
    public Optional < CookieHandler > cookieHandler() {
      return Optional.empty();
    }
    public Optional < Duration > connectTimeout() {
      return Optional.empty();
    }
    public Redirect followRedirects() {
      return Redirect.NEVER;
    }
    public Optional < ProxySelector > proxy() {
      return Optional.empty();
    }
    public SSLContext sslContext() {
      try {
        return SSLContext.getDefault();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    public SSLParameters sslParameters() {
      return new SSLParameters();
    }
    public Optional < Authenticator > authenticator() {
      return Optional.empty();
    }
    public Version version() {
      return Version.HTTP_1_1;
    }
    public Optional < Executor > executor() {
      return Optional.empty();
    }
    public < T > CompletableFuture < HttpResponse < T >> sendAsync(HttpRequest r, HttpResponse.BodyHandler < T > b) {
      throw new UnsupportedOperationException();
    }
    public < T > CompletableFuture < HttpResponse < T >> sendAsync(HttpRequest r, HttpResponse.BodyHandler < T > b, HttpResponse.PushPromiseHandler < T > p) {
      throw new UnsupportedOperationException();
    }
  }
}
