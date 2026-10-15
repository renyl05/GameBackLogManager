package gbm;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
public final class Api {
  public static final class SessionExpired extends IllegalStateException {public SessionExpired(String message){super(message);}}
  public final String base;
  public String token = "";
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
  public Api(String base) {
    this.base = base.replaceAll("/+$", "");
    URI u = URI.create(this.base);
    if (! u.getScheme().equals("https") && !(u.getScheme().equals("http") && Set.of("localhost", "127.0.0.1", "[::1]").contains(u.getHost()))) throw new IllegalArgumentException("Usa HTTPS per un server remoto.");
  }
  public Map < String, Object > call(String route, Map < String, Object > data) throws Exception {
    HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + "/api/" + route)).timeout(Duration.ofSeconds(80)).header("Content-Type", "application/json");
    if (! token.isBlank()) b.header("Authorization", "Bearer " + token);
    HttpResponse < String > r = http.send(b.POST(HttpRequest.BodyPublishers.ofString(Json.write(data))).build(), HttpResponse.BodyHandlers.ofString());
    if(r.statusCode()==502||r.statusCode()==503||r.statusCode()==504)throw new IllegalStateException("Server in riattivazione o temporaneamente non disponibile. Attendi circa un minuto e riprova.");
    Map < String, Object > m = Json.map(Json.read(r.body()));
    if(r.statusCode()==403&&Json.str(m,"error").contains("Sessione scaduta"))throw new SessionExpired(Json.str(m,"error"));
    if (r.statusCode() / 100 != 2) throw new IllegalStateException(Json.str(m, "error"));
    return m;
  }
}
