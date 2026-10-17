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
      api.call("profile-update",Json.obj("username","renato","age","23","gender","Uomo","origin","Salerno","bio","Profilo dimostrativo: videogiochi, musica e liste da condividere."));
      api.call("list-create",Json.obj("title","Da provare insieme","games",List.of("local:minecraft"),"public",true));
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
      SwingUtilities.invokeAndWait(()->{Map<String,Object> empty=new LinkedHashMap<>(state);empty.put("entries",List.of());app[0].state=empty;app[0].stats();});capture(app[0],out.resolve("statistiche-vuote.png"));SwingUtilities.invokeAndWait(()->app[0].state=state);
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
      for(String platform:List.of("PlayStation 5","PC","Nintendo Switch","PC")){
        java.util.concurrent.FutureTask<Void> change=new java.util.concurrent.FutureTask<>(()->{JComboBox<?> choice=(JComboBox<?>)findNamed(app[0].content,"gameEdition");choice.showPopup();choice.setSelectedItem(platform);choice.hidePopup();Component cover=findNamed(app[0].content,"gameCover");if(cover.getWidth()!=186||cover.getHeight()!=262)throw new AssertionError("Edition resized cover: "+platform);return null;});SwingUtilities.invokeLater(change);change.get(4,java.util.concurrent.TimeUnit.SECONDS);
        java.util.concurrent.FutureTask<Void> heartbeat=new java.util.concurrent.FutureTask<>(()->null);SwingUtilities.invokeLater(heartbeat);heartbeat.get(4,java.util.concurrent.TimeUnit.SECONDS);
      }
      System.out.println("PASS edition popup switching keeps EDT responsive");
      bottom(app[0]);capture(app[0],out.resolve("gioco-bottom.png"));
      SwingUtilities.invokeAndWait(()->{app[0].frame.setSize(1100,760);app[0].detail(app[0].game("local:minecraft"));});
      capture(app[0],out.resolve("gioco-small.png"));
      Map<String,Object> profileData=api.call("profile",Json.obj("user",Json.str(Json.map(state.get("me")),"id")));
      SwingUtilities.invokeAndWait(()->{app[0].frame.setSize(1420,900);app[0].renderProfile(app[0].uid(),profileData);});capture(app[0],out.resolve("profilo.png"));
      SwingUtilities.invokeAndWait(()->app[0].renderProfile("other-user",profileData));capture(app[0],out.resolve("profilo-altro.png"));
      SwingUtilities.invokeAndWait(()->{Component logo=findLogo(app[0].frame.getContentPane());logo.dispatchEvent(new java.awt.event.MouseEvent(logo,java.awt.event.MouseEvent.MOUSE_CLICKED,System.currentTimeMillis(),0,15,15,1,false));if(!app[0].page.equals("Menu"))throw new AssertionError("Logo did not return to menu");});System.out.println("PASS logo artwork returns to initial menu");
      java.util.concurrent.atomic.AtomicBoolean staleCallback=new java.util.concurrent.atomic.AtomicBoolean();SwingUtilities.invokeAndWait(()->{if(app[0].busy)throw new AssertionError("Unexpected pending task");app[0].work("Test ritorno al menu",()->{Thread.sleep(200);return true;},r->staleCallback.set(true));app[0].menu();});Thread.sleep(500);SwingUtilities.invokeAndWait(()->{if(staleCallback.get()||!app[0].page.equals("Menu"))throw new AssertionError("Late response overrode menu navigation");});System.out.println("PASS late network callback cannot override logo navigation");
      SwingUtilities.invokeAndWait(() -> app [0].startDemo());
      Thread.sleep(1500);
      SwingUtilities.invokeAndWait(()->{JFrame demo=null;for(Frame f:Frame.getFrames())if(f instanceof JFrame j&&j.isVisible()&&j.getTitle().contains("DEMO LOCALE"))demo=j;if(demo==null)throw new AssertionError("Demo did not open");if(server.db.rows("users").size()!=1)throw new AssertionError("Demo touched original server");for(Frame f:Frame.getFrames())f.dispose();});
      System.out.println("PASS demo opens isolated local server without modifying original account");
    }
    System.exit(0);
  }
  static Component findNamed(Component c,String name){if(c instanceof JComponent j&&name.equals(j.getName()))return c;if(c instanceof Container p)for(Component child:p.getComponents()){Component found=findNamed(child,name);if(found!=null)return found;}return null;}
  static Component findLogo(Component c){if(c instanceof Theme.Logo)return c;if(c instanceof Container p)for(Component child:p.getComponents()){Component found=findLogo(child);if(found!=null)return found;}return null;}
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
