package gbm;
import java.awt.*;import java.awt.image.*;import javax.swing.*;import java.util.*;
/** Exercise complete captions across look-and-feels and display densities. */
public class ButtonLayoutTest {
 public static void main(String[]args)throws Exception{
  for(UIManager.LookAndFeelInfo laf:UIManager.getInstalledLookAndFeels())if(laf.getName().equals("Metal")||laf.getName().equals("Nimbus")){
   UIManager.setLookAndFeel(laf.getClassName());
   SwingUtilities.invokeAndWait(()->{
    for(float textScale:new float[]{1,1.15f}){Theme.scale=textScale;Theme.setup();
     for(String caption:java.util.List.of("Home","Catalogo","Libreria","Statistiche","Impostazioni","Password dimenticata?","Non hai un account? Registrati","Hai già un account? Accedi","Prova senza account · demo locale","Liste in evidenza","Giochi in evidenza","Cerca","Filtri ▾","Posseduti","In corso","Finiti","Da giocare","Desiderati","Esplora le liste","Copia il tuo ID")){
      Theme.PillButton button=(Theme.PillButton)Theme.button(caption,()->{});Dimension d=button.getPreferredSize();button.setSize(d);
      Insets in=button.getInsets();int iw=button.getIcon()==null?0:button.getIcon().getIconWidth(),gap=iw>0?button.getIconTextGap():0;
      if(!button.captionForWidth(d.width-in.left-in.right-iw-gap).equals(button.getText()))throw new AssertionError("Caption clipped: "+caption+" "+laf.getName());
      for(double density:new double[]{1,1.25,1.5,2}){BufferedImage im=new BufferedImage((int)Math.ceil(d.width*density),(int)Math.ceil(d.height*density),BufferedImage.TYPE_INT_ARGB);Graphics2D g=im.createGraphics();g.scale(density,density);button.paint(g);g.dispose();}
     }
     JPanel row=Theme.actionRow(Theme.button("Liste in evidenza",()->{}),Theme.button("Giochi in evidenza",()->{}),Theme.button("Retro",()->{}));row.setSize(row.getPreferredSize());row.doLayout();for(Component c:row.getComponents())if(c instanceof JButton b&&b.getWidth()<b.getPreferredSize().width)throw new AssertionError("Action shrunk");
    }
   });
   System.out.println("PASS complete action captions: "+laf.getName()+"; text 100/115%; density 100/125/150/200%");
  }
  Theme.scale=1;
 }
}
