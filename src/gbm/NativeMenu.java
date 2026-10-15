package gbm;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import static gbm.Theme.*;

/** Original composition rendered as native controls, never as a stretched screenshot. */
public final class NativeMenu extends JPanel {
  final List<MenuTile> tiles=new ArrayList<>();
  final Logo logo=new Logo(225);
  final int[][] rectangles={{97,250,278,453},{396,65,277,308},{695,65,277,308},{994,65,277,308},{396,396,277,307},{695,396,277,307},{994,396,277,307}};
  public NativeMenu(Consumer<String> navigate){setLayout(null);setBackground(PINK);add(logo);for(String name:List.of("Home","Catalogo","Libreria","Amici","Statistiche","Impostazioni","Esci")){MenuTile tile=new MenuTile(name,()->navigate.accept(name));tiles.add(tile);add(tile);}}
  public void doLayout(){double z=Math.min(getWidth()/1366d,getHeight()/768d);int ox=(int)((getWidth()-1366*z)/2),oy=(int)((getHeight()-768*z)/2);logo.setBounds(ox+(int)(110*z),oy+(int)(80*z),(int)(225*z),(int)(113*z));for(int i=0;i<rectangles.length;i++){int[]r=rectangles[i];tiles.get(i).setBounds(ox+(int)(r[0]*z),oy+(int)(r[1]*z),(int)(r[2]*z),(int)(r[3]*z));}}
  static class MenuTile extends JButton {
    final String name;final BufferedImage original;
    MenuTile(String name,Runnable action){this.name=name;BufferedImage image=null;String resource=name.toLowerCase(java.util.Locale.ROOT);try{image=ImageIO.read(Objects.requireNonNull(getClass().getResource("/menu/"+resource+".png")));}catch(Exception e){throw new IllegalStateException("Icona originale mancante",e);}original=image;setText(name);setBorder(null);setContentAreaFilled(false);setOpaque(false);setFocusPainted(false);setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));setToolTipText(name);getAccessibleContext().setAccessibleName(name);addActionListener(e->action.run());}

    protected void paintComponent(Graphics g){Graphics2D a=quality(g);double z=getWidth()/277d;boolean home=name.equals("Home");Shape clip=rounded(0,0,getWidth(),getHeight(),34*z);if(!home){a.setColor(Color.WHITE);a.fill(clip);}
      if(home){a.setPaint(new GradientPaint(0,0,new Color(195,239,251),0,getHeight(),Color.WHITE));a.fill(clip);Path2D hill=new Path2D.Double();hill.moveTo(0,getHeight()*.69);hill.curveTo(getWidth()*.45,getHeight()*.72,getWidth()*.6,getHeight()*.73,getWidth(),getHeight()*.82);hill.lineTo(getWidth(),getHeight());hill.lineTo(0,getHeight());hill.closePath();a.setColor(new Color(199,223,119));Area hillClip=new Area(hill);hillClip.intersect(new Area(clip));a.fill(hillClip);Path2D front=new Path2D.Double();front.moveTo(0,getHeight()*.85);front.curveTo(getWidth()*.5,getHeight()*.70,getWidth()*.65,getHeight()*.76,getWidth(),getHeight()*.81);front.lineTo(getWidth(),getHeight());front.lineTo(0,getHeight());front.closePath();a.setColor(new Color(138,169,0));Area frontClip=new Area(front);frontClip.intersect(new Area(clip));a.fill(frontClip);}
      int side=(int)((home?200:158)*z),cy=home?(int)(getHeight()*.30):(int)(getHeight()*.38),cx=getWidth()/2;
      if(original!=null){double density=Math.abs(a.getTransform().getScaleX());double fit=Math.min(side/(double)Math.max(original.getWidth(),original.getHeight()),1/Math.max(1,density));int w=(int)(original.getWidth()*fit),h=(int)(original.getHeight()*fit);a.drawImage(original,cx-w/2,cy-h/2,w,h,null);}
      int fontSize=(int)((home?52:40)*z);Font f=font(fontSize,true);while(a.getFontMetrics(f).stringWidth(name)>getWidth()-28*z&&fontSize>16)f=font(--fontSize,true);a.setFont(f);a.setColor(name.equals("Libreria")||name.equals("Impostazioni")?BLUE:new Color(160,83,249));FontMetrics fm=a.getFontMetrics();int baseline=home?(int)(getHeight()*.71):(int)(getHeight()*.79);a.drawString(name,(getWidth()-fm.stringWidth(name))/2,baseline);
      if(hasFocus()||getModel().isRollover()){a.setColor(new Color(112,63,225,110));a.setStroke(new BasicStroke(2));a.draw(rounded(2,2,getWidth()-4,getHeight()-4,34*z));}a.dispose();
    }
  }
}
