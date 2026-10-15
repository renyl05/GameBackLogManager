package gbm;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.*;
import javax.swing.plaf.basic.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.imageio.ImageIO;

/** Shared design primitives: one font family, shared geometry and resolution-aware artwork. */
public final class Theme {
  public static final Color PINK=new Color(255,56,189), PURPLE=new Color(133,65,245),
      BLUE=new Color(55,78,239), INK=new Color(58,35,177), LAV=new Color(243,239,255),
      MUTED=new Color(130,116,185), LINE=new Color(230,223,249);
  public static float scale=1f;
  public static boolean contrast=false;
  private static final Font REGULAR=loadFont("Regular"), SEMIBOLD=loadFont("SemiBold"), BOLD=loadFont("Bold");
  private static Font loadFont(String weight) {
    try(InputStream in=Theme.class.getResourceAsStream("/fonts/Fredoka-"+weight+".ttf")) {
      if(in==null)throw new IOException("Font assente");
      return Font.createFont(Font.TRUETYPE_FONT,in);
    }catch(Exception e){throw new ExceptionInInitializerError("Risorsa tipografica mancante: Fredoka-"+weight);}
  }
  public static Font font(int size,boolean bold) {
    return (bold?(size>=24?BOLD:SEMIBOLD):REGULAR).deriveFont(size*scale);
  }
  public static Graphics2D quality(Graphics g) {
    Graphics2D a=(Graphics2D)g.create();
    a.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
    a.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    a.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    a.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    a.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);
    a.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,RenderingHints.VALUE_STROKE_PURE);
    return a;
  }
  static Shape rounded(double x,double y,double w,double h,double radius) {
    double diameter=Math.min(Math.min(w,h),radius*2);
    return new RoundRectangle2D.Double(x,y,w,h,diameter,diameter);
  }
  static Color ink(){return contrast?new Color(35,18,90):INK;}
  public static void setup() {
    for(Object key:Collections.list(UIManager.getDefaults().keys())) {
      if(UIManager.get(key) instanceof Font)UIManager.put(key,new FontUIResource(font(14,false)));
    }
    UIManager.put("Label.font",new FontUIResource(font(14,false)));
    UIManager.put("Button.font",new FontUIResource(font(14,true)));
    UIManager.put("CheckBox.font",new FontUIResource(font(14,false)));
    UIManager.put("CheckBox.icon",new CheckIcon());
    UIManager.put("CheckBox.background",Color.WHITE);
    UIManager.put("CheckBox.foreground",ink());
    UIManager.put("CheckBox.focus",LINE);
    UIManager.put("TextArea.font",new FontUIResource(font(14,false)));
    UIManager.put("TextField.font",new FontUIResource(font(15,false)));
    UIManager.put("PasswordField.font",new FontUIResource(font(15,false)));
    UIManager.put("OptionPane.background",Color.WHITE);
    UIManager.put("Panel.background",Color.WHITE);
    UIManager.put("ScrollPane.border",BorderFactory.createEmptyBorder());
    UIManager.put("ScrollBar.width",10);
    UIManager.put("ToolTip.font",new FontUIResource(font(12,false)));
    UIManager.put("ToolTip.background",LAV);
    UIManager.put("ToolTip.foreground",ink());
    UIManager.put("PopupMenu.border",new RoundedBorder(LINE,14));
    UIManager.put("List.font",new FontUIResource(font(14,false)));
    UIManager.put("List.selectionBackground",LAV);
    UIManager.put("List.selectionForeground",ink());
  }
  public static JLabel label(String text,int size,boolean bold) {
    JLabel l=new JLabel(text){protected void paintComponent(Graphics g){Graphics2D a=quality(g);super.paintComponent(a);a.dispose();}};
    l.setFont(font(size,bold));l.setForeground(ink());return l;
  }
  public static JLabel muted(String text){JLabel l=label(text,13,false);l.setForeground(contrast?new Color(72,57,102):MUTED);return l;}
  public static JPanel row(Component...children){JPanel p=new JPanel(new FlowLayout(FlowLayout.LEFT,10,6));p.setOpaque(false);for(Component c:children)p.add(c);return p;}
  /** Applies the same spacing to initial and subsequently appended children. */
  static class Stack extends JPanel {
    Stack(){setOpaque(false);setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));}
    protected void addImpl(Component c,Object constraints,int index){
      if(c instanceof JComponent j)j.setAlignmentX(Component.LEFT_ALIGNMENT);
      if(getComponentCount()>0&&index==-1)super.addImpl(Box.createVerticalStrut(12),null,-1);
      super.addImpl(c,constraints,index);
    }
  }
  public static JPanel col(Component...children){JPanel p=new Stack();for(Component c:children)p.add(c);return p;}
  public static JPanel actionRow(Component...children){JPanel p=new JPanel(new FlowLayout(FlowLayout.LEFT,8,0));p.setOpaque(false);for(Component c:children)p.add(c);return p;}
  public static JPanel card(){JPanel p=new RoundPanel(Color.WHITE,24);p.setBorder(new CompoundBorder(new RoundedBorder(LINE,24),new EmptyBorder(18,18,18,18)));p.setLayout(new BorderLayout(12,12));return p;}
  public static JScrollPane scroll(Component c){JPanel wrapper=new WidthPanel();wrapper.add(c);JScrollPane s=new JScrollPane(wrapper);s.setBorder(null);s.getViewport().setBackground(Color.WHITE);s.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);styleScrollBar(s.getVerticalScrollBar());return s;}
  static void styleScrollBar(JScrollBar bar){bar.setUnitIncrement(26);bar.setPreferredSize(new Dimension(10,10));bar.setUI(new BasicScrollBarUI(){
    protected JButton createDecreaseButton(int d){return zeroButton();}protected JButton createIncreaseButton(int d){return zeroButton();}
    JButton zeroButton(){JButton b=new JButton();b.setPreferredSize(new Dimension(0,0));b.setMinimumSize(new Dimension(0,0));return b;}
    protected void paintTrack(Graphics g,JComponent c,Rectangle r){}
    protected void paintThumb(Graphics g,JComponent c,Rectangle r){if(!bar.isEnabled())return;Graphics2D a=quality(g);a.setColor(LINE);a.fill(rounded(r.x+2,r.y,r.width-4,r.height,5));a.dispose();}
  });}
  public static JButton button(String text,Runnable action){return button(text,false,action);}
  public static JButton button(String text,boolean primary,Runnable action){return new PillButton(text,primary,false,false,action);}
  public static JButton navigation(String text,boolean selected,Runnable action){return new PillButton(text,false,true,selected,action);}
  static class PillButton extends JButton {
    final boolean primary,nav,selected;
    PillButton(String text,boolean primary,boolean nav,boolean selected,Runnable action){super(text);this.primary=primary;this.nav=nav;this.selected=selected;
      if(text.equals("♥")||text.equals("♡")){setIcon(new HeartIcon(text.equals("♥")));setText("");getAccessibleContext().setAccessibleName("Preferito");}
      if(text.endsWith(" ▾")){setText(text.substring(0,text.length()-2));setIcon(new ChevronIcon());setHorizontalTextPosition(SwingConstants.LEFT);setIconTextGap(16);}
      setFont(font(14,true));setForeground(nav?(selected?INK:Color.WHITE):primary?Color.WHITE:INK);
      setOpaque(false);setContentAreaFilled(false);setBorder(new EmptyBorder(10,13,10,13));setFocusPainted(false);
      setRolloverEnabled(true);setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));addActionListener(e->action.run());
    }
    public Dimension getMaximumSize(){return getPreferredSize();}
    protected void paintComponent(Graphics g){Graphics2D a=quality(g);Color bg=nav?(selected?Color.WHITE:null):primary?PURPLE:LAV;
      if(!isEnabled())bg=LINE;else if(getModel().isPressed()&&bg!=null)bg=bg.darker();else if(getModel().isRollover())bg=nav&&!selected?new Color(255,255,255,35):bg==null?null:bg.darker();
      if(bg!=null){a.setColor(bg);a.fill(this.getParent()!=null&&"avatar".equals(getParent().getName())?new Ellipse2D.Double(0,0,getWidth(),getHeight()):rounded(0,0,getWidth(),getHeight(),22));}
      if(hasFocus()){a.setColor(nav?Color.WHITE:BLUE);a.setStroke(new BasicStroke(1.5f));a.draw(rounded(2,2,getWidth()-4,getHeight()-4,20));}
      super.paintComponent(a);a.dispose();
    }
  }
  public static JButton avatar(String text,Runnable action){
    JButton b=new PillButton(text,false,false,false,action){
      public Dimension getPreferredSize(){return new Dimension(52,52);}
      public boolean contains(int x,int y){return new Ellipse2D.Double(0,0,getWidth(),getHeight()).contains(x,y);}
    };
    b.setBorder(new EmptyBorder(0,0,0,0));return b;
  }
  static class HeartIcon implements Icon {final boolean filled;HeartIcon(boolean f){filled=f;}public int getIconWidth(){return 21;}public int getIconHeight(){return 21;}
    public void paintIcon(Component c,Graphics g,int x,int y){Graphics2D a=quality(g);a.translate(x,y);Path2D p=new Path2D.Double();p.moveTo(10.5,18);p.curveTo(8,15,1.5,11,1.5,6);p.curveTo(1.5,1,8,-.2,10.5,4);p.curveTo(13,-.2,19.5,1,19.5,6);p.curveTo(19.5,11,13,15,10.5,18);p.closePath();a.setColor(PURPLE);if(filled)a.fill(p);else{a.setStroke(new BasicStroke(1.8f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));a.draw(p);}a.dispose();}}
  static class ChevronIcon implements Icon{public int getIconWidth(){return 14;}public int getIconHeight(){return 14;}public void paintIcon(Component c,Graphics g,int x,int y){Graphics2D a=quality(g);a.setColor(c.getForeground());a.setStroke(new BasicStroke(2,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));Path2D p=new Path2D.Double();p.moveTo(x+2,y+5);p.lineTo(x+7,y+10);p.lineTo(x+12,y+5);a.draw(p);a.dispose();}}
  public static class StyledCheckBox extends JCheckBox {
    public StyledCheckBox(String text){this(text,false);}
    public StyledCheckBox(String text,boolean selected){super(text,selected);setOpaque(false);setBorder(new EmptyBorder(0,0,0,0));}
    protected void paintComponent(Graphics g){Graphics2D a=quality(g);super.paintComponent(a);a.dispose();}
  }
  static class CheckIcon implements Icon{public int getIconWidth(){return 21;}public int getIconHeight(){return 21;}public void paintIcon(Component c,Graphics g,int x,int y){Graphics2D a=quality(g);boolean on=c instanceof AbstractButton b&&b.isSelected();a.setColor(on?PURPLE:LAV);a.fill(rounded(x+1,y+1,18,18,5));a.setColor(on?PURPLE:LINE);a.setStroke(new BasicStroke(1.5f));a.draw(rounded(x+1,y+1,18,18,5));if(on){a.setColor(Color.WHITE);a.setStroke(new BasicStroke(2,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));Path2D p=new Path2D.Double();p.moveTo(x+5,y+10);p.lineTo(x+9,y+14);p.lineTo(x+15,y+6);a.draw(p);}a.dispose();}}
  public static <T>JComboBox<T> combo(T[] values){JComboBox<T> c=new JComboBox<>(values){protected void paintComponent(Graphics g){Graphics2D a=quality(g);a.setColor(LAV);a.fill(rounded(0,0,getWidth(),getHeight(),16));super.paintComponent(a);a.dispose();}};
    c.setOpaque(false);c.setBackground(LAV);c.setForeground(ink());c.setFont(font(14,false));c.setBorder(new CompoundBorder(new RoundedBorder(LINE,16),new EmptyBorder(3,10,3,6)));
    c.setMaximumSize(new Dimension(Integer.MAX_VALUE,44));c.setPreferredSize(new Dimension(c.getPreferredSize().width+12,44));
    c.setRenderer(new DefaultListCellRenderer(){public Component getListCellRendererComponent(JList<?> l,Object v,int i,boolean selected,boolean focus){JLabel a=(JLabel)super.getListCellRendererComponent(l,v,i,selected,false);a.setFont(font(14,false));a.setForeground(ink());a.setBorder(new EmptyBorder(7,10,7,10));a.setOpaque(i>=0);a.setBackground(selected?LAV:Color.WHITE);return a;}});
    c.setUI(new BasicComboBoxUI(){protected JButton createArrowButton(){JButton b=new JButton(new ChevronIcon());b.setForeground(INK);b.setBorder(new EmptyBorder(4,8,4,8));b.setContentAreaFilled(false);b.setOpaque(false);return b;}public void paintCurrentValueBackground(Graphics g,Rectangle r,boolean f){}protected ComboPopup createPopup(){BasicComboPopup p=new BasicComboPopup(comboBox);p.setBorder(new CompoundBorder(new RoundedBorder(LINE,14),new EmptyBorder(6,2,6,2)));return p;}});
    return c;
  }
  static void inputBackground(Graphics g,JComponent c){Graphics2D a=quality(g);a.setColor(LAV);a.fill(rounded(0,0,c.getWidth(),c.getHeight(),16));a.dispose();}
  public static void styleInput(JTextComponent f){f.setAlignmentX(Component.LEFT_ALIGNMENT);f.setMaximumSize(new Dimension(Integer.MAX_VALUE,46));f.setOpaque(false);f.setFont(font(15,false));f.setForeground(ink());f.setCaretColor(PURPLE);f.setBackground(LAV);f.setSelectionColor(new Color(219,200,255));f.setBorder(new CompoundBorder(new RoundedBorder(LINE,16),new EmptyBorder(10,14,10,14)));}
  public static JTextField field(String hint){JTextField f=new JTextField(24){protected void paintComponent(Graphics g){inputBackground(g,this);Graphics2D a=quality(g);super.paintComponent(a);if(getText().isEmpty()&&!hasFocus()){a.setFont(getFont());a.setColor(MUTED);a.drawString(hint,getInsets().left,(getHeight()+getFontMetrics(getFont()).getAscent()-getFontMetrics(getFont()).getDescent())/2);}a.dispose();}};styleInput(f);f.getAccessibleContext().setAccessibleName(hint);f.setToolTipText(hint);return f;}
  public static JPasswordField passwordField(int columns){JPasswordField p=new JPasswordField(columns){protected void paintComponent(Graphics g){inputBackground(g,this);Graphics2D a=quality(g);super.paintComponent(a);a.dispose();}};styleInput(p);return p;}
  public static JSpinner ratingSpinner(double initial){JSpinner s=new JSpinner(new SpinnerNumberModel(initial,0d,10d,.5d)){protected void paintComponent(Graphics g){inputBackground(g,this);super.paintComponent(g);}};
    s.setOpaque(false);s.setBorder(new CompoundBorder(new RoundedBorder(LINE,16),new EmptyBorder(5,8,5,5)));s.setPreferredSize(new Dimension(98,40));
    s.setUI(new BasicSpinnerUI(){protected Component createNextButton(){JButton b=arrow(true);installNextButtonListeners(b);return b;}protected Component createPreviousButton(){JButton b=arrow(false);installPreviousButtonListeners(b);return b;}JButton arrow(boolean up){JButton b=new JButton(up?"+":"−");b.setFont(font(13,true));b.setForeground(PURPLE);b.setOpaque(false);b.setContentAreaFilled(false);b.setBorder(new EmptyBorder(0,5,0,5));return b;}});
    if(s.getEditor() instanceof JSpinner.DefaultEditor e){e.setOpaque(false);e.getTextField().setOpaque(false);e.getTextField().setForeground(ink());e.getTextField().setFont(font(15,true));e.getTextField().setBorder(null);}return s;
  }
  public static JScrollPane textBox(JTextArea area){JScrollPane s=new JScrollPane(area){public void paint(Graphics g){Graphics2D a=quality(g);a.clip(rounded(0,0,getWidth(),getHeight(),16));super.paint(a);a.dispose();}};s.setOpaque(false);s.getViewport().setOpaque(false);s.setBorder(new RoundedBorder(LINE,16));styleScrollBar(s.getVerticalScrollBar());area.setBorder(new EmptyBorder(12,14,12,14));area.setFont(font(14,false));area.setBackground(LAV);area.setForeground(ink());return s;}
  public static class WidthPanel extends JPanel implements Scrollable{WidthPanel(){super(new BorderLayout());setOpaque(false);}public Dimension getPreferredScrollableViewportSize(){return getPreferredSize();}public int getScrollableUnitIncrement(Rectangle r,int o,int d){return 26;}public int getScrollableBlockIncrement(Rectangle r,int o,int d){return Math.max(26,r.height-40);}public boolean getScrollableTracksViewportWidth(){return true;}public boolean getScrollableTracksViewportHeight(){return getPreferredSize().height<getParent().getHeight();}}
  public static class RoundedBorder extends AbstractBorder{final Color color;final int radius;public RoundedBorder(Color c,int r){color=c;radius=r;}public Insets getBorderInsets(Component c){return new Insets(1,1,1,1);}public void paintBorder(Component c,Graphics g,int x,int y,int w,int h){Graphics2D a=quality(g);a.setStroke(new BasicStroke(1));a.setColor(color);a.draw(rounded(x+.5,y+.5,w-1,h-1,radius));a.dispose();}}
  public static class RoundPanel extends JPanel{final Color color;final int radius;public RoundPanel(Color c,int r){color=c;radius=r;setOpaque(false);}protected void paintComponent(Graphics g){Graphics2D a=quality(g);a.setColor(color);a.fill(rounded(0,0,getWidth(),getHeight(),radius));a.dispose();super.paintComponent(g);}}
  public static class Logo extends JComponent{
    static final Path2D path=readPath();static Path2D readPath(){Path2D p=new Path2D.Double(Path2D.WIND_EVEN_ODD);try(BufferedReader r=new BufferedReader(new InputStreamReader(Objects.requireNonNull(Logo.class.getResourceAsStream("/logo.path"))))){for(String line;(line=r.readLine())!=null;){String[]v=line.split(" ");switch(v[0]){case "M"->p.moveTo(Double.parseDouble(v[1]),Double.parseDouble(v[2]));case "Q"->p.quadTo(Double.parseDouble(v[1]),Double.parseDouble(v[2]),Double.parseDouble(v[3]),Double.parseDouble(v[4]));case "Z"->p.closePath();default->throw new IOException("Logo non valido");}}}catch(Exception e){throw new ExceptionInInitializerError("Contorno originale del logo mancante");}return p;}
    public Logo(int width){setPreferredSize(new Dimension(width,Math.round(width*113f/225)));setMaximumSize(getPreferredSize());setMinimumSize(getPreferredSize());getAccessibleContext();setToolTipText("Game Backlog Manager");}
    static final BufferedImage raster=readRaster();
    static BufferedImage readRaster(){try{BufferedImage src=ImageIO.read(Objects.requireNonNull(Logo.class.getResourceAsStream("/logo.png")));BufferedImage out=new BufferedImage(src.getWidth(),src.getHeight(),BufferedImage.TYPE_INT_ARGB);for(int y=0;y<src.getHeight();y++)for(int x=0;x<src.getWidth();x++){int c=src.getRGB(x,y);int r=(c>>16)&255,g=(c>>8)&255,b=c&255;if(g>170&&r>180&&b<150)out.setRGB(x,y,c|0xff000000);}return out;}catch(IOException e){throw new ExceptionInInitializerError(e);}}
    protected void paintComponent(Graphics g){Graphics2D a=(Graphics2D)g.create();a.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);double z=Math.min(getWidth()/225d,getHeight()/113d);int w=(int)Math.round(225*z),h=(int)Math.round(113*z);a.drawImage(raster,(getWidth()-w)/2,(getHeight()-h)/2,w,h,null);a.dispose();}
  }
  static final Map<String,BufferedImage> images=Collections.synchronizedMap(new LinkedHashMap<>(32,.75f,true){protected boolean removeEldestEntry(Map.Entry<String,BufferedImage>e){return size()>24;}});
  static final Set<String> failed=ConcurrentHashMap.newKeySet(),loading=ConcurrentHashMap.newKeySet();
  static final ExecutorService imagePool=Executors.newFixedThreadPool(4,r->{Thread t=new Thread(r,"cover-loader");t.setDaemon(true);return t;});
  public static class Art extends JComponent {
    final String url,title;boolean contain;volatile BufferedImage source;BufferedImage rendered;int cachedW,cachedH;BufferedImage cachedSource;
    public Art(String url,String title,int width,int height){this.url=hiRes(url);this.title=title;setPreferredSize(new Dimension(width,height));setMinimumSize(new Dimension(50,60));source=images.get(this.url);
      if(source==null&&!this.url.isBlank()&&!failed.contains(this.url)){imagePool.submit(()->{try{BufferedImage im=download(this.url);if(im==null&&this.url.endsWith("/library_hero.jpg"))im=download(this.url.replace("/library_hero.jpg","/header.jpg"));if(im==null)throw new IOException("Immagine non disponibile");source=im;images.put(this.url,im);}catch(Exception e){failed.add(this.url);}finally{SwingUtilities.invokeLater(this::repaint);}});}}
    static String hiRes(String u){return u.replace("/t_cover_big/","/t_cover_big_2x/").replace("/t_screenshot_big/","/t_1080p/").replace("/header.jpg","/library_hero.jpg");}
    static BufferedImage download(String u)throws Exception{URI uri=URI.create(u);String host=uri.getHost();if(!"https".equals(uri.getScheme())||host==null||!(host.equals("images.igdb.com")||host.endsWith(".steamstatic.com")))throw new IOException("Host immagine non consentito");URLConnection c=uri.toURL().openConnection();c.setConnectTimeout(8000);c.setReadTimeout(10000);try(InputStream in=c.getInputStream()){byte[]b=in.readNBytes(12000001);if(b.length>12000000)throw new IOException("Immagine troppo grande");return ImageIO.read(new ByteArrayInputStream(b));}catch(FileNotFoundException missing){return null;}}
    protected void paintComponent(Graphics g){Graphics2D a=quality(g);double density=Math.max(1,Math.abs(a.getTransform().getScaleX()));int w=Math.max(1,(int)Math.ceil(getWidth()*density)),h=Math.max(1,(int)Math.ceil(getHeight()*density));BufferedImage im=source;
      if(rendered==null||cachedW!=w||cachedH!=h||cachedSource!=im){rendered=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);Graphics2D raw=rendered.createGraphics();Graphics2D p=quality(raw);raw.dispose();p.setColor(Color.WHITE);p.fill(rounded(0,0,w,h,16*density));p.setComposite(AlphaComposite.SrcIn);
        if(im!=null){double z=contain?Math.min(w/(double)im.getWidth(),h/(double)im.getHeight()):Math.max(w/(double)im.getWidth(),h/(double)im.getHeight());int iw=(int)Math.ceil(im.getWidth()*z),ih=(int)Math.ceil(im.getHeight()*z);p.drawImage(im,(w-iw)/2,(h-ih)/2,iw,ih,null);}else{p.setPaint(new GradientPaint(0,0,new Color(164,94,250),w,h,PINK));p.fillRect(0,0,w,h);}p.dispose();cachedW=w;cachedH=h;cachedSource=im;}
      a.drawImage(rendered,0,0,getWidth(),getHeight(),null);
      if(im==null){a.setColor(Color.WHITE);a.setFont(font(17,true));FontMetrics fm=a.getFontMetrics();List<String>lines=new ArrayList<>();String line="";for(String word:title.split(" ")){String next=line.isEmpty()?word:line+" "+word;if(fm.stringWidth(next)>getWidth()-24&&!line.isEmpty()){lines.add(line);line=word;}else line=next;}if(!line.isEmpty())lines.add(line);int y=getHeight()/2-lines.size()*fm.getHeight()/2;for(String l:lines){a.drawString(l,Math.max(12,(getWidth()-fm.stringWidth(l))/2),y+=fm.getHeight());}a.setFont(font(10,false));a.drawString("Copertina non disponibile",10,getHeight()-12);}
      a.dispose();
    }
  }
}
