package gbm;

import java.io.IOException;
import java.sql.*;
import java.util.*;

/** Single-writer persistent snapshots for ephemeral hosting; optimistic revision guard. */
final class SnapshotDatabase {
  final String url;
  final Properties credentials = new Properties();
  long revision;

  SnapshotDatabase(Properties cfg) throws IOException {
    url = setting(cfg, "JDBC_DATABASE_URL", "");
    if(url.startsWith("jdbc:postgresql:")&&url.contains("?"))throw new IOException("Usa JDBC_DATABASE_URL senza parametri: host e database soltanto. TLS verificato viene impostato dall’app.");
    String user = setting(cfg, "JDBC_DATABASE_USER", "");
    String password = setting(cfg, "JDBC_DATABASE_PASSWORD", "");
    if (!user.isEmpty()) credentials.setProperty("user", user);
    if (!password.isEmpty()) credentials.setProperty("password", password);
    credentials.setProperty("connectTimeout", "15");
    credentials.setProperty("socketTimeout", "30");
    if (url.startsWith("jdbc:postgresql:")) {
      // Use publicly trusted certificates and hostname verification for Neon.
      credentials.setProperty("sslmode", "verify-full");
      credentials.setProperty("sslfactory", "org.postgresql.ssl.DefaultJavaSSLFactory");
    }
    try {
      Class.forName(url.startsWith("jdbc:h2:") ? "org.h2.Driver" : "org.postgresql.Driver");
    } catch (ClassNotFoundException e) {
      throw new IOException("Driver database non trovato. Avvia con lib/postgresql.jar nel classpath.");
    }
  }

  static String setting(Properties cfg, String key, String fallback) {
    if("true".equals(cfg.getProperty("GBM_TEST_ISOLATED")))return cfg.getProperty(key,fallback);
    return System.getenv().getOrDefault(key, cfg.getProperty(key, fallback));
  }

  Connection connect() throws SQLException { return DriverManager.getConnection(url, credentials); }

  Map<String,Object> load() throws IOException {
    try (Connection c = connect()) {
      try (Statement st=c.createStatement()) {
        st.executeUpdate("CREATE TABLE IF NOT EXISTS gbm_state (id INTEGER PRIMARY KEY, payload TEXT NOT NULL, revision BIGINT NOT NULL)");
      }
      try (PreparedStatement insert=c.prepareStatement("INSERT INTO gbm_state (id,payload,revision) VALUES (1,?,0)")) {
        insert.setString(1,"{}");
        try { insert.executeUpdate(); }
        catch (SQLException exists) { if (!"23505".equals(exists.getSQLState())) throw exists; }
      }
      try (Statement st=c.createStatement(); ResultSet rs=st.executeQuery("SELECT payload,revision FROM gbm_state WHERE id=1")) {
        if (!rs.next()) throw new SQLException("Snapshot non presente");
        revision=rs.getLong(2);
        return Json.map(Json.read(rs.getString(1)));
      }
    } catch (SQLException e) { throw new IOException("Connessione al database non riuscita. Verifica configurazione, TLS e disponibilità."); }
  }

  void save(Map<String,Object> root) throws IOException {
    try (Connection c=connect(); PreparedStatement st=c.prepareStatement("UPDATE gbm_state SET payload=?,revision=revision+1 WHERE id=1 AND revision=?")) {
      st.setString(1,Json.write(root));
      st.setLong(2,revision);
      if (st.executeUpdate()!=1) throw new IOException("Un'altra istanza ha aggiornato i dati: riavvia questo server. Non avviare due server sullo stesso database.");
      revision++;
    } catch (SQLException e) { throw new IOException("Salvataggio remoto non riuscito. Nessun ripiego su disco temporaneo: controlla il database."); }
  }
}
