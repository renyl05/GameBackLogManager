package gbm;

import javax.swing.*;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.util.*;

/** Optional visual QA with downloaded artwork; never loaded by the application. */
public final class ArtworkPreview {
  public static void main(String[] args)throws Exception{
    Properties cfg=new Properties();cfg.setProperty("GBM_TEST_ISOLATED","true");
    try(Server server=new Server(Files.createTempDirectory("gbm-artwork").resolve("db.json"),0,cfg)){
      server.start();Api api=new Api("http://127.0.0.1:"+server.port());
      api.token=Json.str(api.call("register",Json.obj("email","preview@example.invalid","username","preview","password","preview-test-123")),"token");
      Map<String,Object> state=api.call("state",Json.obj());
      Map<String,Object> game=new LinkedHashMap<>(Json.map(Json.list(state.get("games")).get(0)));
      String cover="https://cdn.akamai.steamstatic.com/steam/apps/1245620/library_600x900_2x.jpg",hero="https://cdn.akamai.steamstatic.com/steam/apps/1245620/library_hero.jpg";
      Theme.images.put(Theme.Art.hiRes(cover),ImageIO.read(Path.of(args[0],"cover-qa.jpg").toFile()));
      Theme.images.put(Theme.Art.hiRes(hero),ImageIO.read(Path.of(args[0],"hero-qa.jpg").toFile()));
      game.putAll(Json.obj("name","Elden Ring","year","2022","genres","Action RPG","platforms","PC, PlayStation 5, Nintendo Switch","cover",cover,"hero",hero,"summary","Esplora l’Interregno, affronta le sue sfide e costruisci il tuo personaggio. Anteprima di verifica dell’interfaccia con artwork reale; i dati dell’account sono temporanei."));
      java.util.List<Map<String,Object>> games=new ArrayList<>();for(int i=0;i<24;i++){Map<String,Object> item=new LinkedHashMap<>(game);item.put("id",i==0?game.get("id"):"preview:"+i);games.add(item);}state.put("games",games);
      for(boolean dark:new boolean[]{false,true}){
        App[] app={null};SwingUtilities.invokeAndWait(()->{Theme.palette(dark);Theme.setup();app[0]=new App(api);Theme.palette(dark);Theme.setup();app[0].state=state;app[0].frame.setSize(1420,900);app[0].menu();app[0].frame.setVisible(true);});
        Path out=Path.of(args[0],"working/qa/artwork/"+(dark?"dark":"light"));Files.createDirectories(out);
        VisualTest.capture(app[0],out.resolve("menu.png"));
        SwingUtilities.invokeAndWait(()->app[0].catalog(false,"catalog",games));VisualTest.capture(app[0],out.resolve("catalogo.png"));
        SwingUtilities.invokeAndWait(()->app[0].detail(game));Thread.sleep(400);VisualTest.capture(app[0],out.resolve("gioco.png"));
        SwingUtilities.invokeAndWait(()->{app[0].frame.setSize(1100,760);app[0].frame.validate();});VisualTest.capture(app[0],out.resolve("gioco-small.png"));
        SwingUtilities.invokeAndWait(()->app[0].frame.dispose());
      }
    }
    System.out.println("PASS visual previews with real artwork (local isolated data)");System.exit(0);
  }
}
