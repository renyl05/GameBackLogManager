package gbm;
import javax.swing.*;
import java.awt.*;
import java.awt.image.*;
import java.util.*;
/** Regression tests for native menu hit targets, typography and HiDPI image masks. */
public final class ThemeTest {
  static void ok(boolean b,String message){IntegrationTest.check(b,message);}
  public static void main(String[]args)throws Exception{
    SwingUtilities.invokeAndWait(()->{
      Theme.setup();
      String family=Theme.font(14,false).getFamily();
      ok(family.toLowerCase(Locale.ROOT).contains("fredoka"),"embedded rounded font loaded");
      ok(Theme.label("Prova",14,false).getFont().getFamily().equals(family),"label uses shared typeface");
      ok(Theme.passwordField(12).getFont().getFamily().equals(family),"password uses shared typeface");
      ok(Theme.combo(new String[]{"A","B"}).getFont().getFamily().equals(family),"dropdown uses shared typeface");
      ok(UIManager.getFont("CheckBox.font").getFamily().equals(family),"checkbox uses shared typeface");
      ok(!Theme.Logo.path.getBounds2D().isEmpty(),"original logo outlines loaded");
      java.util.List<String> clicked=new ArrayList<>();NativeMenu menu=new NativeMenu(clicked::add);menu.setSize(1420,900);menu.doLayout();
      ok(menu.tiles.size()==7,"seven real menu buttons");
      for(int i=0;i<menu.tiles.size();i++){JButton b=menu.tiles.get(i);ok(menu.getBounds().contains(b.getBounds()),"menu bounds: "+b.getText());for(int j=i+1;j<menu.tiles.size();j++)ok(!b.getBounds().intersects(menu.tiles.get(j).getBounds()),"menu tiles never overlap");b.doClick(0);}
      ok(clicked.equals(java.util.List.of("Home","Catalogo","Libreria","Amici","Statistiche","Impostazioni","Esci")),"menu actions include exit");
      String url="https://images.igdb.com/igdb/image/upload/t_cover_big/check.png";
      BufferedImage source=new BufferedImage(800,1200,BufferedImage.TYPE_INT_RGB);Graphics2D a=source.createGraphics();a.setColor(Color.BLUE);a.fillRect(0,0,800,1200);a.dispose();Theme.images.put(Theme.Art.hiRes(url),source);
      Theme.Art art=new Theme.Art(url,"Test",180,270);art.setSize(180,270);BufferedImage image=new BufferedImage(360,540,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();g.scale(2,2);art.paint(g);g.dispose();
      ok(art.cachedW==360&&art.cachedH==540,"artwork raster matches display density");
      ok((image.getRGB(0,0)>>>24)==0,"artwork rounded corner remains transparent");
      ok((image.getRGB(180,270)&0xffffff)==0x0000ff,"artwork content survives rounded mask");
      ok(Theme.Art.hiRes(url).contains("t_cover_big_2x"),"IGDB high-resolution cover requested");
      JButton heart=Theme.button("♥",()->{});ok(heart.getIcon()!=null&&heart.getText().isEmpty(),"favorite rendered as vector icon, not missing font glyph");
    });
    System.out.println("PASS UI regressions");
  }
}
