package gbm;
import javax.swing.*;
import java.awt.*;
import static gbm.Theme.*;

/** Layout checks for long text, theme and window changes without network dependencies. */
public final class HeroLayoutTest {
  public static void main(String[] args)throws Exception{
    SwingUtilities.invokeAndWait(()->{
      for(boolean night:new boolean[]{false,true})for(float scale:new float[]{1f,1.15f}){
        Theme.palette(night);Theme.scale=scale;Theme.setup();
        JTextArea description=App.wrap("Una descrizione lunga che deve restare completamente leggibile anche riducendo la finestra. ".repeat(15),10000);
        JPanel info=col(label("Gioco con descrizione estesa",26,true),description);
        JPanel cover=new JPanel(new BorderLayout());cover.add(new Art("","Cover",174,250));
        JPanel controls=col(actionRow(button("+ Libreria",()->{}),new StyledCheckBox("Preferito",false)),combo(new String[]{"PC","Nintendo Switch"}),label("Il tuo voto / 10",14,true),button("Salva",()->{}));
        GameHero hero=new GameHero(new Art("","",1200,230),cover,info,controls);
        for(int width:new int[]{800,1050,1200,1320}){
          hero.setSize(width,100);hero.setSize(width,hero.getPreferredSize().height);hero.doLayout();
          Rectangle a=hero.information.getBounds(),b=hero.actions.getBounds();
          check(!a.intersects(b),"information and controls overlap");
          check(hero.getBounds().contains(a)&&hero.getBounds().contains(b),"panels escape background");
          check(hero.backdrop.getBounds().equals(new Rectangle(0,0,hero.getWidth(),hero.getHeight())),"background stops before section end");
          check(cover.getWidth()==186&&cover.getHeight()==262,"cover dimensions vary with layout");
          check(hero.summary.getHeight()>=hero.summaryHeight(hero.summary.getWidth()),"long summary clipped");
        }
        App.GameGrid grid=new App.GameGrid();
        check(grid.columns(1320)==(scale==1f?8:7),"unexpected compact grid columns");
      }
    });
    System.out.println("PASS hero panels, long descriptions, fixed cover, compact grid, light/dark and text scaling");
  }
  static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
