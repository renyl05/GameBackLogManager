package gbm;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import java.awt.*;
import java.awt.image.*;
import javax.imageio.*;
/** Test-only synthetic data. Not loaded by the application. */
public class VisualTest {
  public static void main(String [] args) throws Exception {
    Path temp = Files.createTempDirectory("gbm-visual"), out = Path.of(args.length > 0 ? args [0] : "qa");
    Files.createDirectories(out);
    Properties isolated=new Properties();isolated.setProperty("GBM_TEST_ISOLATED","true");
    try (Server server = new Server(temp.resolve("db.json"), 0, isolated)) {
      server.start();
      Api api = new Api("http://127.0.0.1:" + server.port());
      api.token = Json.str(api.call("register", Json.obj("email", "qa@example.com", "username", "renato", "password", "test-visuale-123")), "token");
      for (String [] r : new String [] [] {
        {
          "local:minecraft", "IN_CORSO"
        }, {
          "local:elden-ring", "FINITO"
        }, {
          "local:cyberpunk-2077", "DESIDERATO"
        }
      }) api.call("entry", Json.obj("game", r [0], "status", r [1], "favorite", true, "rating", 8));
      api.call("entry",Json.obj("game","local:minecraft","platform","PlayStation 5","status","FINITO","favorite",true,"rating",8));
      Map < String, Object > state = api.call("state", Json.obj());
      App [] app = {
        null
      };
      SwingUtilities.invokeAndWait(() -> {
        app [0] = new App(api);Theme.palette(Boolean.getBoolean("gbm.qa.dark"));Theme.setup();app[0].auth(false);
        app [0].frame.setVisible(true);
      });
      SwingUtilities.invokeAndWait(()->app[0].frame.getContentPane().requestFocusInWindow());
      capture(app [0], out.resolve("accesso.png"));
      SwingUtilities.invokeAndWait(()->app[0].auth(true));
      capture(app[0],out.resolve("registrazione.png"));
      SwingUtilities.invokeAndWait(() -> {
        app [0].state = state;
        app [0].menu();
      });
      capture(app [0], out.resolve("menu.png"));
      for (String name : List.of("Home", "Catalogo", "Libreria", "Amici", "Statistiche", "Impostazioni")) {
        SwingUtilities.invokeAndWait(() -> app [0].navigate(name));
        capture(app [0], out.resolve(name.toLowerCase() + ".png"));
        if(name.equals("Statistiche")||name.equals("Impostazioni")){bottom(app[0]);capture(app[0],out.resolve(name.toLowerCase()+"-bottom.png"));}
      }
      SwingUtilities.invokeAndWait(() -> {
        app [0].frame.setSize(1100, 760);
        app [0].settings();
      });
      capture(app [0], out.resolve("impostazioni-small.png"));
      SwingUtilities.invokeAndWait(() -> {
        app [0].frame.setSize(1420, 900);
        app [0].detail(app [0].game("local:minecraft"));
      });
      Thread.sleep(1000);
      capture(app [0], out.resolve("gioco.png"));
      bottom(app[0]);capture(app[0],out.resolve("gioco-bottom.png"));
      SwingUtilities.invokeAndWait(() -> app [0].startDemo());
      Thread.sleep(1500);
      SwingUtilities.invokeAndWait(()->{JFrame demo=null;for(Frame f:Frame.getFrames())if(f instanceof JFrame j&&j.isVisible()&&j.getTitle().contains("DEMO LOCALE"))demo=j;if(demo==null)throw new AssertionError("Demo did not open");if(server.db.rows("users").size()!=1)throw new AssertionError("Demo touched original server");for(Frame f:Frame.getFrames())f.dispose();});
      System.out.println("PASS demo opens isolated local server without modifying original account");
    }
    System.exit(0);
  }
  static void bottom(App app)throws Exception{SwingUtilities.invokeAndWait(()->{for(Component c:app.content.getComponents())if(c instanceof JScrollPane sc)sc.getVerticalScrollBar().setValue(sc.getVerticalScrollBar().getMaximum());});}
  static void capture(App app, Path p) throws Exception {
    Thread.sleep(150);
    SwingUtilities.invokeAndWait(() -> {
      try {
        app.frame.validate();
        double density=Double.parseDouble(System.getProperty("gbm.qa.scale","1"));
        BufferedImage im = new BufferedImage((int)(app.frame.getWidth()*density), (int)(app.frame.getHeight()*density), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();g.scale(density,density);
        app.frame.paint(g);
        g.dispose();
        ImageIO.write(im, "png", p.toFile());
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
  }
}
