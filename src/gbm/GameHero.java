package gbm;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import static gbm.Theme.*;

/** Full section artwork with readable information and edition panels. */
final class GameHero extends JPanel {
  final Art backdrop;
  final JPanel information, actions, summary;
  final JComponent cover;
  static final int COVER_WIDTH=174, COVER_HEIGHT=250;

  GameHero(Art backdrop,JComponent cover,JPanel summary,JPanel controls){
    super(null);setOpaque(false);this.backdrop=backdrop;backdrop.decorative=true;this.cover=cover;this.summary=summary;
    Color glass=new Color(SURFACE.getRed(),SURFACE.getGreen(),SURFACE.getBlue(),dark?232:232);
    information=new RoundPanel(glass,24);information.setLayout(null);
    information.add(cover);information.add(summary);information.setName("gameInformation");
    actions=new RoundPanel(glass,24);actions.setLayout(new BorderLayout());
    actions.setBorder(new EmptyBorder(20,20,20,20));actions.add(controls);actions.setName("gameActions");
    cover.setName("gameCover");setName("gameHero");
    add(information);add(actions);add(backdrop);
  }

  int summaryHeight(int width){
    for(Component c:summary.getComponents())if(c instanceof JTextArea t)t.setSize(Math.max(40,width),32767);
    summary.invalidate();return summary.getPreferredSize().height;
  }
  int[] dimensions(int width){
    int padding=24,gap=20,available=Math.max(300,width-2*padding);
    boolean stacked=width<1080;
    int actionWidth=stacked?available:Math.min(450,Math.max(410,width*36/100));
    int infoWidth=stacked?available:available-actionWidth-gap;
    int textWidth=Math.max(60,infoWidth-COVER_WIDTH-64);
    int infoHeight=Math.max(COVER_HEIGHT+12,summaryHeight(textWidth))+40;
    int actionHeight=actions.getPreferredSize().height;
    int height=100+(stacked?infoHeight+gap+actionHeight:Math.max(infoHeight,actionHeight))+24;
    return new int[]{infoWidth,infoHeight,actionWidth,actionHeight,height};
  }
  public Dimension getPreferredSize(){int width=getWidth()>0?getWidth():1200;return new Dimension(1000,dimensions(width)[4]);}
  public Dimension getMinimumSize(){return new Dimension(360,getPreferredSize().height);}
  public void doLayout(){
    int width=getWidth();int[] d=dimensions(width);boolean stacked=width<1080;
    backdrop.setBounds(0,0,width,getHeight());
    information.setBounds(24,100,d[0],d[1]);
    actions.setBounds(stacked?24:width-24-d[2],stacked?120+d[1]:100,d[2],d[3]);
    cover.setBounds(20,20,COVER_WIDTH+12,COVER_HEIGHT+12);
    summary.setBounds(COVER_WIDTH+44,20,Math.max(60,d[0]-COVER_WIDTH-64),d[1]-40);
  }
}
