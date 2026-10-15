package gbm;
import javax.net.ssl.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Small SMTP STARTTLS client for password-reset delivery; no insecure SMTP authentication. */
public final class Mailer {
  final Properties cfg;
  final java.net.http.HttpClient http;
  public Mailer(Properties cfg) {this(cfg,java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build());}
  Mailer(Properties cfg,java.net.http.HttpClient http){this.cfg=cfg;this.http=http;}
  String get(String k){return SnapshotDatabase.setting(cfg,k,"");}

  public boolean ready() {
    return (!get("SMTP_HOST").isBlank()||!get("BREVO_API_KEY").isBlank())&&!get("SMTP_FROM").isBlank();
  }
  static class Channel {
    Socket socket;
    BufferedReader in;
    BufferedWriter out;
    Channel(Socket s) throws Exception {
      socket = s;
      reset();
    }
    void reset() throws Exception {
      in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
      out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }
    int response() throws Exception {
      String line = in.readLine();
      if (line == null || line.length() < 3) throw new IOException("Risposta SMTP non valida");
      int code = Integer.parseInt(line.substring(0, 3));
      while (line.length() > 3 && line.charAt(3) == '-') {
        line = in.readLine();
        if (line == null) throw new IOException("SMTP interrotto");
      }
      return code;
    }
    void expect(int wanted) throws Exception {
      if (response() != wanted) throw new IOException("Invio email non riuscito");
    }
    void send(String line, int wanted) throws Exception {
      out.write(line + "\r\n");
      out.flush();
      expect(wanted);
    }
  }
  public void reset(String email, String code) throws Exception {
    if (! ready()) throw new IllegalStateException("Servizio email non configurato");
    String host = get("SMTP_HOST"), from = get("SMTP_FROM");
    if (! from.matches("[^\\s<>@]+@[^\\s<>@]+")) throw new IllegalStateException("Mittente SMTP non valido");
    if(!get("BREVO_API_KEY").isBlank()){
      String body=Json.write(Json.obj("sender",Json.obj("email",from,"name","Game Backlog Manager"),"to",List.of(Json.obj("email",email)),"subject","Game Backlog Manager - recupero password","textContent","Il tuo codice di recupero: "+code+"\nScade tra 15 minuti. Se non hai richiesto il recupero, ignora questa email."));
      java.net.http.HttpRequest req=java.net.http.HttpRequest.newBuilder(URI.create("https://api.brevo.com/v3/smtp/email")).timeout(java.time.Duration.ofSeconds(20)).header("api-key",get("BREVO_API_KEY")).header("Content-Type","application/json").POST(java.net.http.HttpRequest.BodyPublishers.ofString(body)).build();
      var response=http.send(req,java.net.http.HttpResponse.BodyHandlers.ofString());
      if(response.statusCode()/100!=2)throw new IOException("Invio email non riuscito: verifica chiave API e mittente autorizzato su Brevo.");
      return;
    }
    int port = Integer.parseInt(get("SMTP_PORT").isBlank() ? "587" : get("SMTP_PORT"));
    Socket socket = new Socket();
    socket.connect(new InetSocketAddress(host, port), 10000);
    socket.setSoTimeout(15000);
    try {
      Channel c = new Channel(socket);
      c.expect(220);
      c.send("EHLO gamebacklog.local", 250);
      c.send("STARTTLS", 220);
      SSLSocket tls =(SSLSocket)((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(socket, host, port, true);
      SSLParameters params = tls.getSSLParameters();
      params.setEndpointIdentificationAlgorithm("HTTPS");
      tls.setSSLParameters(params);
      tls.startHandshake();
      c.socket = tls;
      c.reset();
      c.send("EHLO gamebacklog.local", 250);
      if (! get("SMTP_USER").isBlank()) c.send("AUTH PLAIN " + Base64.getEncoder().encodeToString(("\0" + get("SMTP_USER") + "\0" + get("SMTP_PASSWORD")).getBytes(StandardCharsets.UTF_8)), 235);
      c.send("MAIL FROM:<" + from + ">", 250);
      c.send("RCPT TO:<" + email + ">", 250);
      c.send("DATA", 354);
      String body = "From: " + from + "\r\nTo: " + email +("\r\nSubject: Game Backlog Manager - recupero password\r\nMIME-Version: " + "1.0\r\nContent-Type: text/plain; charset=UTF-8\r\nContent-Transfer-Encoding: " + "8bit\r\n\r\nIl tuo codice di recupero: ") + code + "\r\nScade tra 15 minuti. Se non hai richiesto il recupero, ignora questa email.\r\n.\r\n";
      c.out.write(body);
      c.out.flush();
      c.expect(250);
      c.send("QUIT", 221);
      tls.close();
    } finally {
      socket.close();
    }
  }
}
