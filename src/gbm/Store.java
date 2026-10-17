package gbm;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.io.*;
/** Atomic snapshots; all server mutations are serialized by Server. */
public final class Store {
  public final Path path;
  final SnapshotDatabase database;
  public Map < String, Object > root;
  public Store(Path path) throws IOException {this(path,new Properties());}
  public Store(Path path,Properties cfg) throws IOException {
    this.path = path;
    database=SnapshotDatabase.setting(cfg,"JDBC_DATABASE_URL","").isBlank()?null:new SnapshotDatabase(cfg);
    root = database!=null ? database.load() : Files.exists(path) ? Json.map(Json.read(Files.readString(path))) : Json.obj();
    for (String k : List.of("users", "entries", "reviews", "requests", "links", "snapshots", "lists", "activities", "consoleLinks")) root.computeIfAbsent(k, z -> new ArrayList < >());
    root.computeIfAbsent("games", z -> seed());
    for(Object o:rows("entries")){Map<String,Object> e=Json.map(o);if(!e.containsKey("editions")){List<Object> editions=new ArrayList<>();String status=Json.str(e,"status");if(!status.isBlank()){Map<String,Object> game=find("games","id",Json.str(e,"game"));String platforms=game==null?"":Json.str(game,"platforms");String platform=platforms.isBlank()||platforms.contains(",")?"Edizione non specificata":platforms;editions.add(Json.obj("platform",platform,"status",status));}e.put("editions",editions);}}
    Map<String,Object> minecraft=find("games","id","local:minecraft");if(minecraft!=null)minecraft.put("platforms","PC, PlayStation 4, PlayStation 5, Xbox One, Nintendo Switch");
  }
  public List < Object > rows(String name) {
    return Json.list(root.get(name));
  }
  public void save() throws IOException {
    if(database!=null){database.save(root);return;}
    Files.createDirectories(path.toAbsolutePath().getParent());
    Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
    Files.writeString(tmp, Json.write(root), StandardCharsets.UTF_8);
    try {
      Files.setPosixFilePermissions(tmp, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
    } catch (UnsupportedOperationException ignored) {
    }
    try {
      Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
    }
  }
  public Map < String, Object > find(String table, String key, String value) {
    for (Object o : rows(table)) {
      Map < String, Object > m = Json.map(o);
      if (Json.str(m, key).equals(value)) return m;
    }
    return null;
  }
  static List < Object > seed() {
    List < Object > a = new ArrayList < >();
    String [] [] g = {
      {
        "minecraft", "Minecraft", "PC", "Sandbox"
      }, {
        "elden-ring", "Elden Ring", "PC, PlayStation, Xbox", "Action RPG"
      }, {
        "red-dead-redemption-2", "Red Dead Redemption 2", "PC, PlayStation, Xbox", "Azione"
      }, {
        "cyberpunk-2077", "Cyberpunk 2077", "PC, PlayStation, Xbox", "GDR"
      }, {
        "the-last-of-us-part-i", "The Last of Us Part I", "PC, PlayStation 5", "Avventura"
      }
    };
    for (String [] v : g) a.add(Json.obj("id", "local:" + v [0], "name", v [1], "platforms", v [2], "genres", v [3], "cover", "", "hero", "", "summary", "Titolo del catalogo originale. Configura IGDB per descrizioni, copertine e catalogo " + "multipiattaforma.", "source", "originale"));
    return a;
  }
}
