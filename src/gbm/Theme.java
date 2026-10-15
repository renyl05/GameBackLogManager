package gbm;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
public final class Theme {
  public static final Color PINK = new Color(255, 56, 189), PURPLE = new Color(95, 31, 237), BLUE = new Color(46, 48, 205), LAV = new Color(242, 238, 255), MUTED = new Color(125, 113, 180), LINE = new Color(231, 225, 248);
  public static float scale = 1f;
  public static boolean contrast = false;
  static final Map < String, BufferedImage > images = new ConcurrentHashMap < >();
  static final Set < String > failed = ConcurrentHashMap.newKeySet(), loading = ConcurrentHashMap.newKeySet();
  static final ExecutorService imagePool = Executors.newFixedThreadPool(4, r -> {
    Thread t = new Thread(r);
    t.setDaemon(true);
    return t;
  });
  public static Font font(int size, boolean bold) {
    return new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, Math.round(size * scale));
  }
  public static void setup() {
    UIManager.put("Label.font", font(14, false));
    UIManager.put("Button.font", font(14, true));
    UIManager.put("TextField.font", font(15, false));
    UIManager.put("PasswordField.font", font(15, false));
    UIManager.put("ComboBox.font", font(14, false));
    UIManager.put("TextArea.font", font(14, false));
    UIManager.put("OptionPane.background", Color.WHITE);
    UIManager.put("Panel.background", Color.WHITE);
    UIManager.put("ScrollBar.width", 10);
  }
  public static JLabel label(String text, int size, boolean bold) {
    JLabel l = new JLabel(text);
    l.setFont(font(size, bold));
    l.setForeground(contrast ? new Color(35, 18, 90) : BLUE);
    return l;
  }
  public static JLabel muted(String text) {
    JLabel l = label(text, 13, false);
    l.setForeground(contrast ? new Color(72, 57, 102) : MUTED);
    return l;
  }
  public static JPanel row(Component...c) {
    JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
    p.setOpaque(false);
    for (Component x : c) p.add(x);
    return p;
  }
  public static JPanel col(Component...c) {
    JPanel p = new JPanel();
    p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
    p.setOpaque(false);
    for (Component x : c) {
      if (x instanceof JComponent j) j.setAlignmentX(Component.LEFT_ALIGNMENT);
      p.add(x);
      p.add(Box.createVerticalStrut(12));
    }
    return p;
  }
  public static JPanel card() {
    JPanel p = new RoundPanel(Color.WHITE, 26);
    p.setBorder(new CompoundBorder(new RoundedBorder(LINE, 24), new EmptyBorder(18, 18, 18, 18)));
    p.setLayout(new BorderLayout(12, 12));
    return p;
  }
  public static JScrollPane scroll(Component c) {
    JPanel wrapper = new WidthPanel();
    wrapper.add(c, BorderLayout.CENTER);
    JScrollPane s = new JScrollPane(wrapper);
    s.setBorder(null);
    s.getViewport().setBackground(Color.WHITE);
    s.getVerticalScrollBar().setUnitIncrement(24);
    s.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    return s;
  }
  public static JButton button(String text, Runnable action) {
    return button(text, false, action);
  }
  public static JButton button(String text, boolean primary, Runnable action) {
    JButton b = new JButton(text) {
      protected void paintComponent(Graphics g) {
        Graphics2D a =(Graphics2D) g.create();
        a.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        a.setColor(isEnabled() ?(primary ? PURPLE : LAV) : LINE);
        a.fillRoundRect(0, 0, getWidth(), getHeight(), 28, 28);
        a.dispose();
        super.paintComponent(g);
      }
    };
    b.setFont(font(14, true));
    b.setForeground(primary ? Color.WHITE : BLUE);
    b.setContentAreaFilled(false);
    b.setFocusPainted(true);
    b.setBorder(new EmptyBorder(11, 16, 11, 16));
    b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    b.addActionListener(e -> action.run());
    return b;
  }
  public static JButton navigation(String text, boolean selected, Runnable action) {
    JButton b = new JButton(text) {
      protected void paintComponent(Graphics g) {
        if (selected) {
          Graphics2D a =(Graphics2D) g.create();
          a.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
          a.setColor(Color.WHITE);
          a.fillRoundRect(0, 0, getWidth(), getHeight(), 30, 30);
          a.dispose();
        }
        super.paintComponent(g);
      }
    };
    b.setContentAreaFilled(false);
    b.setBorder(new EmptyBorder(12, 15, 12, 15));
    b.setForeground(selected ? BLUE : Color.WHITE);
    b.setFont(font(14, true));
    b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    b.addActionListener(e -> action.run());
    return b;
  }
  public static < T > JComboBox < T > combo(T [] values) {
    JComboBox < T > c = new JComboBox < >(values);
    c.setBackground(LAV);
    c.setForeground(BLUE);
    c.setBorder(new RoundedBorder(LINE, 16));
    c.setFont(font(14, false));
    c.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
    return c;
  }
  public static JTextField field(String hint) {
    JTextField f = new JTextField(24) {
      protected void paintComponent(Graphics g) {
        Graphics2D bg =(Graphics2D) g.create();
        bg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        bg.setColor(LAV);
        bg.fillRoundRect(0, 0, getWidth(), getHeight(), 28, 28);
        bg.dispose();
        super.paintComponent(g);
        if (getText().isEmpty() && ! hasFocus()) {
          Graphics2D a =(Graphics2D) g.create();
          a.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
          a.setFont(getFont());
          a.setColor(MUTED);
          a.drawString(hint, 15, (getHeight() + getFontMetrics(getFont()).getAscent() - getFontMetrics(getFont()).getDescent()) / 2);
          a.dispose();
        }
      }
    };
    f.setOpaque(false);
    f.setFont(font(15, false));
    f.setForeground(BLUE);
    f.setBackground(LAV);
    f.setBorder(BorderFactory.createCompoundBorder(new RoundedBorder(LINE, 28), new EmptyBorder(12, 14, 12, 14)));
    f.getAccessibleContext().setAccessibleName(hint);
    f.setToolTipText(hint);
    return f;
  }
  public static class WidthPanel extends JPanel implements Scrollable {
    WidthPanel() {
      super(new BorderLayout());
      setOpaque(false);
    }
    public Dimension getPreferredScrollableViewportSize() {
      return getPreferredSize();
    }
    public int getScrollableUnitIncrement(Rectangle r, int o, int d) {
      return 24;
    }
    public int getScrollableBlockIncrement(Rectangle r, int o, int d) {
      return Math.max(24, r.height - 40);
    }
    public boolean getScrollableTracksViewportWidth() {
      return true;
    }
    public boolean getScrollableTracksViewportHeight() {
      return getPreferredSize().height < getParent().getHeight();
    }
  }
  public static class RoundedBorder extends AbstractBorder {
    final Color color;
    final int r;
    public RoundedBorder(Color c, int r) {
      color = c;
      this.r = r;
    }
    public Insets getBorderInsets(Component c) {
      return new Insets(1, 1, 1, 1);
    }
    public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
      Graphics2D a =(Graphics2D) g.create();
      a.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      a.setColor(color);
      a.drawRoundRect(x, y, w - 1, h - 1, r, r);
      a.dispose();
    }
  }
  public static class RoundPanel extends JPanel {
    final Color color;
    final int radius;
    public RoundPanel(Color c, int r) {
      color = c;
      radius = r;
      setOpaque(false);
    }
    protected void paintComponent(Graphics g) {
      Graphics2D a =(Graphics2D) g.create();
      a.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      a.setColor(color);
      a.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
      a.dispose();
      super.paintComponent(g);
    }
  }
  public static class Logo extends JComponent {
    static BufferedImage image;
    static {
      try {
        image = ImageIO.read(Objects.requireNonNull(Logo.class.getResource("/logo.png")));
      } catch (Exception ignored) {
      }
    }
    public Logo(int width) {
      setPreferredSize(new Dimension(width, Math.round(width * 113f / 225)));
      setMaximumSize(getPreferredSize());
      setMinimumSize(getPreferredSize());
    }
    protected void paintComponent(Graphics g) {
      if (image != null) {
        double z = Math.min(getWidth() /(double) image.getWidth(), getHeight() /(double) image.getHeight());
        Graphics2D a =(Graphics2D) g.create();
        a.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        a.drawImage(image, 0, 0, (int)(image.getWidth() * z), (int)(image.getHeight() * z), null);
        a.dispose();
      }
    }
  }
  public static class Art extends JComponent {
    final String url, title;
    final int radius;
    public Art(String url, String title, int width, int height) {
      this.url = url;
      this.title = title;
      radius = 18;
      setPreferredSize(new Dimension(width, height));
      setMinimumSize(new Dimension(50, 60));
      if (! url.isBlank() && ! images.containsKey(url) && ! failed.contains(url) && loading.add(url)) imagePool.submit(() -> {
        try {
          URI uri = URI.create(url);
          String host = uri.getHost();
          if (! "https".equals(uri.getScheme()) || host == null || !(host.equals("images.igdb.com") || host.endsWith(".steamstatic.com"))) throw new IllegalArgumentException();
          URLConnection conn = uri.toURL().openConnection();
          conn.setConnectTimeout(8000);
          conn.setReadTimeout(10000);
          try (var in = conn.getInputStream()) {
            byte [] bytes = in.readNBytes(12000001);
            if (bytes.length > 12000000) throw new IllegalArgumentException();
            BufferedImage im = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
            if (im != null) images.put(url, im);
            else failed.add(url);
          }
        } catch (Exception e) {
          failed.add(url);
        } finally {
          loading.remove(url);
          SwingUtilities.invokeLater(() -> {
            for (Window w : Window.getWindows()) w.repaint();
          });
        }
      });
    }
    protected void paintComponent(Graphics g) {
      Graphics2D a =(Graphics2D) g.create();
      a.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      a.setClip(new java.awt.geom.RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), radius, radius));
      BufferedImage im = images.get(url);
      if (im != null) {
        double z = Math.max((double) getWidth() / im.getWidth(), (double) getHeight() / im.getHeight());
        int w =(int)(im.getWidth() * z), h =(int)(im.getHeight() * z);
        a.drawImage(im, (getWidth() - w) / 2, (getHeight() - h) / 2, w, h, null);
      } else {
        a.setPaint(new GradientPaint(0, 0, new Color(166, 95, 255), getWidth(), getHeight(), PINK));
        a.fillRect(0, 0, getWidth(), getHeight());
        a.setColor(new Color(255, 255, 255, 35));
        a.fillOval(getWidth() / 2, - 30, getWidth(), getWidth());
        a.setColor(Color.WHITE);
        a.setFont(font(16, true));
        FontMetrics fm = a.getFontMetrics();
        String [] words = title.split(" ");
        int y = getHeight() / 2 - words.length * 10;
        for (String word : words) {
          while (fm.stringWidth(word) > getWidth() - 20 && word.length() > 3) word = word.substring(0, word.length() - 1);
          a.drawString(word, Math.max(8, (getWidth() - fm.stringWidth(word)) / 2), y += 22);
        }
        a.setFont(font(10, false));
        a.drawString("Copertina non disponibile", 10, getHeight() - 14);
      }
      a.dispose();
    }
  }
}
