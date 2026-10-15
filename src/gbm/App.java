package gbm;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.function.*;
import java.util.prefs.Preferences;
import static gbm.Theme.*;
/** Swing desktop client. Network and disk operations never run on the EDT. */
public final class App {
  final JFrame frame = new JFrame("Game Backlog Manager");
  final Api api;
  Map < String, Object > state = Json.obj();
  final Preferences prefs = Preferences.userNodeForPackage(App.class);
  String page = "Menu";
  JPanel content;
  JLabel status = muted(" ");
  boolean busy;
  static Server local;
  public App(Api api) {
    this.api = api;
    Theme.scale = prefs.getFloat("textScale", 1);
    Theme.contrast = prefs.getBoolean("contrast", false);
    Theme.setup();
    frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    frame.setMinimumSize(new Dimension(Theme.scale>1?1180:980, 700));
    frame.setSize(1420, 900);
    frame.setLocationRelativeTo(null);
    frame.addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) {
        if (local != null) local.close();
      }
    });
    auth(false);
  }
  static Map < String, Object > m(Object x) {
    return Json.map(x);
  }
  static String s(Map < String, Object > x, String k) {
    return Json.str(x, k);
  }
  static List < Object > l(Object x) {
    return Json.list(x);
  }
  static Map < String, Object > obj(Object...v) {
    return Json.obj(v);
  }
  String uid() {
    return s(m(state.get("me")), "id");
  }
  List < Map < String, Object >> rows(String k) {
    return l(state.get(k)).stream().map(Json::map).toList();
  }
  Map < String, Object > game(String id) {
    return rows("games").stream().filter(g -> s(g, "id").equals(id)).findFirst().orElse(obj("id", id, "name", "Gioco non disponibile"));
  }
  Map < String, Object > entry(String id) {
    return rows("entries").stream().filter(e -> s(e, "game").equals(id)).findFirst().orElse(obj("game", id, "status", "", "favorite", false, "rating", null));
  }
  static String esc(String s) {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
  }
  static JTextArea wrap(String text, int width) {
    JTextArea area = new JTextArea(text) {
      @Override public Dimension getPreferredSize() {
        int w = getWidth()>20?Math.min(width,getWidth()):width;
        FontMetrics metrics=getFontMetrics(getFont());int lines=0;
        for(String line:getText().split("\\n",-1))lines+=Math.max(1,(int)Math.ceil(metrics.stringWidth(line)/(double)Math.max(30,w-8)));
        return new Dimension(w,lines*metrics.getHeight()+8);
      }
      @Override public Dimension getMaximumSize(){return new Dimension(Integer.MAX_VALUE,getPreferredSize().height);}
      @Override public Dimension getMinimumSize(){return new Dimension(0,getPreferredSize().height);}
    };
    area.setEditable(false);area.setOpaque(false);area.setLineWrap(true);area.setWrapStyleWord(true);
    area.setFont(font(14,false));area.setForeground(Theme.contrast?new Color(35,18,90):BLUE);
    return area;
  }

  void error(Throwable e) {
    if(e instanceof Api.SessionExpired){api.token="";state=obj();auth(false);}

    String msg = e.getMessage();
    if (msg == null || msg.isBlank()) msg = "Connessione non riuscita. Verifica che il server sia avviato.";
    JOptionPane.showMessageDialog(frame, msg, "Operazione non riuscita", JOptionPane.ERROR_MESSAGE);
  }
  < T > void work(String message, java.util.concurrent.Callable < T > job, Consumer < T > done) {
    if (busy) {
      return;
    }
    busy = true;
    status.setText(message);
    frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    new SwingWorker < T, Void >() {
      protected T doInBackground() throws Exception {
        return job.call();
      }
      protected void done() {
        busy = false;
        frame.setCursor(Cursor.getDefaultCursor());
        status.setText(" ");
        try {
          done.accept(get());
        } catch (Exception e) {
          error(e.getCause() == null ? e : e.getCause());
        }
      }
    }
    .execute();
  }
  void reload(Runnable then) {
    work("Aggiornamento…", () -> api.call("state", obj()), a -> {
      state = a;
      then.run();
    });
  }
  void mutate(String route, Map < String, Object > data, Runnable next) {
    work("Salvataggio…", () -> {
      Map < String, Object > r = api.call(route, data);
      state = api.call("state", obj());
      return r;
    }, r -> next.run());
  }
  void shell(String selected) {
    page = selected;
    JPanel root = new JPanel(new BorderLayout(14, 12));
    root.setBackground(PINK);
    root.setBorder(new EmptyBorder(14, 22, 14, 22));
    JPanel nav = new JPanel(new BorderLayout(15, 0));
    nav.setOpaque(false);
    JButton logo = new JButton();
    logo.setLayout(new BorderLayout());
    logo.add(new Logo(145));
    logo.setBorder(null);
    logo.setContentAreaFilled(false);
    logo.addActionListener(e -> menu());
    nav.add(logo, BorderLayout.WEST);
    JPanel links = row();
    for (String name : List.of("Home", "Catalogo", "Libreria", "Amici", "Statistiche", "Impostazioni")) {
      JButton b = navigation(name, name.equals(selected), () -> navigate(name));
      links.add(b);
    }
    nav.add(links, BorderLayout.CENTER);
    JButton profile = button(s(m(state.get("me")), "username"), () -> settings());
    String profileName=s(m(state.get("me")),"username");profile.setText(profileName.length()>1?profileName.substring(0,2).toUpperCase(Locale.ROOT):profileName.toUpperCase(Locale.ROOT));profile.setToolTipText(s(m(state.get("me")),"handle"));profile.setPreferredSize(new Dimension(52,52));profile.setBorder(new EmptyBorder(8,8,8,8));
    nav.add(profile, BorderLayout.EAST);
    root.add(nav, BorderLayout.NORTH);
    content = new RoundPanel(Color.WHITE, 34);
    content.setBorder(new EmptyBorder(22, 24, 22, 24));
    content.setLayout(new BorderLayout(10, 16));
    root.add(content, BorderLayout.CENTER);
    status = muted(" ");
    status.setForeground(Color.WHITE);
    root.add(status, BorderLayout.SOUTH);
    frame.setContentPane(root);
    frame.revalidate();
    frame.repaint();
  }
  void navigate(String name) {
    switch (name) {
      case "Home" -> home();
      case "Catalogo" -> { if(Json.yes(state,"igdb"))loadCatalog("","catalog",0);else catalog(false,"catalog",null); }
      case "Libreria" -> catalog(true, "library", null);
      case "Amici" -> friends();
      case "Statistiche" -> stats();
      case "Impostazioni" -> settings();
    }
  }
  void title(String text) {
    content.add(label(text, 30, true), BorderLayout.NORTH);
  }
  void showBody(Component body) {
    content.add(scroll(body), BorderLayout.CENTER);
    frame.revalidate();
    frame.repaint();
  }
  void auth(boolean register) {
    JPanel root = new JPanel(new GridBagLayout());
    root.setBackground(PINK);
    JPanel form = new RoundPanel(Color.WHITE, 36);
    form.setBorder(new EmptyBorder(28, 38, 28, 38));
    form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
    JTextField email = field("Email"), username = field("Nome utente");
    JPasswordField pw = passwordField(24), confirm = passwordField(24);
    for(JPasswordField f:List.of(pw,confirm))styleInput(f);

    form.add(label(register ? "Crea il tuo account" : "Bentornato", 28, true));
    form.add(Box.createVerticalStrut(22));
    if (register) {
      form.add(label("Nome utente", 14, true));
      form.add(username);
      form.add(muted("L'ID sarà assegnato automaticamente."));
      form.add(Box.createVerticalStrut(12));
    }
    form.add(label("Email", 14, true));
    form.add(email);
    form.add(Box.createVerticalStrut(12));
    form.add(label("Password", 14, true));
    form.add(pw);
    form.add(Box.createVerticalStrut(10));
    JCheckBox show = new JCheckBox("Mostra password");
    show.setOpaque(false);
    show.setForeground(BLUE);
    char echo = pw.getEchoChar();
    show.addActionListener(e -> {
      pw.setEchoChar(show.isSelected() ?(char) 0 : echo);
      confirm.setEchoChar(show.isSelected() ?(char) 0 : echo);
    });
    form.add(show);
    if (register) {
      form.add(label("Conferma password", 14, true));
      form.add(confirm);
      form.add(muted("Almeno 10 caratteri. Libreria inizialmente privata."));
    }
    form.add(Box.createVerticalStrut(18));
    JButton submit = button(register ? "Registrati" : "Accedi", true, () -> {
      String password = new String(pw.getPassword());
      if (register && ! password.equals(new String(confirm.getPassword()))) {
        error(new IllegalArgumentException("Le password non coincidono"));
        return;
      }
      work(register ? "Registrazione…" : "Accesso…", () -> {
        Map < String, Object > r = api.call(register ? "register" : "login", obj("email", email.getText(), "password", password, "username", username.getText()));
        api.token = s(r, "token");
        return api.call("state", obj());
      }, r -> {
        state = r;
        menu();
      });
    });
    form.add(submit);
    form.add(Box.createVerticalStrut(12));
    if (! register) form.add(button("Password dimenticata?", () -> resetPassword(email.getText())));
    form.add(button(register ? "Hai già un account? Accedi" : "Non hai un account? Registrati", () -> auth(! register)));
    JPanel logoRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
    logoRow.setOpaque(false);
    logoRow.add(new Logo(225));
    JPanel box = col(logoRow, Box.createVerticalStrut(14), form);
    root.add(box);
    status = muted(" ");
    root.add(status, new GridBagConstraints() {
      {
        gridx = 0;
        gridy = 1;
        insets = new Insets(10, 0, 0, 0);
      }
    });
    frame.setContentPane(root);
    frame.getRootPane().setDefaultButton(submit);
    frame.revalidate();
    frame.repaint();
  }
  void menu() {
    page = "Menu";
    JPanel root = new JPanel(new BorderLayout());
    root.setBackground(PINK);
    root.add(new NativeMenu(name->{if(name.equals("Esci"))frame.dispatchEvent(new WindowEvent(frame,WindowEvent.WINDOW_CLOSING));else navigate(name);}));
    status = muted(" ");
    frame.setContentPane(root);
    frame.revalidate();
    frame.repaint();
  }

  String stateName(String x) {
    return switch (x) {
      case "FINITO" -> "Finito";
      case "IN_CORSO" -> "In corso";
      case "DA_GIOCARE" -> "Da giocare";
      case "DESIDERATO" -> "Desiderato";
      default -> "Non in libreria";
    };
  }
  JPanel grid(List < Map < String, Object >> games, boolean personal) {
    if (games.isEmpty()) return col(label("Nessun gioco da mostrare", 20, true), muted("Prova una ricerca diversa o aggiungi un gioco dal catalogo."));
    JPanel panel = new GameGrid();
    for (Map < String, Object > g : games) panel.add(gameCard(g, personal));
    return panel;
  }
  static class GameGrid extends JPanel implements Scrollable {
    GameGrid() {
      setLayout(null);
      setOpaque(false);
    }
    int columns(int w) {
      return Math.max(3, Math.min(6, (w + 14) / 190));
    }
    int rowHeight() {
      return Math.round(345 * Theme.scale);
    }
    public Dimension getPreferredSize() {
      int w = getParent() != null ? getParent().getWidth() : getWidth();
      if (w < 100) w = 1260;
      int cols = columns(w);
      return new Dimension(w, ((getComponentCount() + cols - 1) / cols) *(rowHeight() + 18));
    }
    public void doLayout() {
      int cols = columns(getWidth()), w =(getWidth() - 14 *(cols - 1)) / cols, h = rowHeight();
      for (int i = 0; i < getComponentCount(); i ++) getComponent(i).setBounds((i % cols) *(w + 14), (i / cols) *(h + 18), w, h);
    }
    public Dimension getPreferredScrollableViewportSize() {
      return new Dimension(1250, 700);
    }
    public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) {
      return 28;
    }
    public int getScrollableBlockIncrement(Rectangle r, int o, int d) {
      return rowHeight();
    }
    public boolean getScrollableTracksViewportWidth() {
      return true;
    }
    public boolean getScrollableTracksViewportHeight() {
      return false;
    }
  }
  JPanel gameCard(Map < String, Object > g, boolean personal) {
    JPanel card = new RoundPanel(Color.WHITE, 20);
    card.setBorder(new CompoundBorder(new RoundedBorder(LINE, 20), new EmptyBorder(8, 8, 10, 8)));
    card.setLayout(new BorderLayout(4, 8));
    Art art = new Art(s(g, "cover"), s(g, "name"), 180, 255);
    art.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    art.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        detail(g);
      }
    });
    card.add(art);
    Map < String, Object > e = entry(s(g, "id"));
    JButton name = button(s(g, "name"), () -> detail(g));
    name.setContentAreaFilled(false);
    name.setHorizontalAlignment(SwingConstants.LEFT);
    name.setBorder(new EmptyBorder(5, 2, 5, 2));
    name.setToolTipText(s(g, "name"));
    JButton heart = button(Json.yes(e, "favorite") ? "♥" : "♡", () -> saveEntry(g, s(e, "status"), ! Json.yes(e, "favorite"), e.get("rating"), () -> navigate(page)));
    heart.setBorder(new EmptyBorder(6, 7, 6, 7));
    JPanel line = new JPanel(new BorderLayout());
    line.setOpaque(false);
    line.add(name);
    line.add(heart, BorderLayout.EAST);
    JPanel bottom = new JPanel(new BorderLayout());
    bottom.setOpaque(false);
    bottom.add(line);
    if (personal) {
      bottom.add(muted(stateName(s(e, "status")) +(e.get("rating") == null ? "" : "   Voto " + e.get("rating") + "/10")), BorderLayout.SOUTH);
    }
    card.add(bottom, BorderLayout.SOUTH);
    return card;
  }
  void saveEntry(Map < String, Object > g, String status, boolean favorite, Object rating, Runnable next) {
    mutate("entry", obj("game", g.get("id"), "status", status, "favorite", favorite, "rating", rating), next);
  }
  void catalog(boolean library, String mode, List < Map < String, Object >> provided) {
    shell(library ? "Libreria" : "Catalogo");
    JPanel top = col();
    JPanel heading = row(label(library ? "La tua libreria" : mode.equals("retro") ? "Retro" : mode.equals("featured") ? "Giochi in evidenza" : "Catalogo", 28, true));
    if (library) heading.add(muted(s(m(state.get("me")), "visibility").equals("private") ? "Privata" : s(m(state.get("me")), "visibility").equals("friends") ? "Solo amici" : "Pubblica"));
    top.add(heading);
    JTextField search = field("Cerca un gioco…");
    search.setColumns(38);
    if (provided != null && ! mode.equals("list")) search.setText(catalogQuery);
    search.setToolTipText("Scrivi il nome del gioco");
    String [] platforms = {
      "Tutte le piattaforme", "PC", "PlayStation", "Xbox", "Nintendo", "Game Boy", "NES", "SNES", "Sega"
    };
    JComboBox < String > platform = combo(platforms);
    JComboBox < String > genre = combo(new String [] {
      "Tutti i generi", "Azione", "Avventura", "RPG", "GDR", "Sandbox", "Platform", "Strategy", "Puzzle"
    });
    JCheckBox favorite = new JCheckBox("Solo preferiti");
    favorite.setOpaque(false);
    favorite.setForeground(BLUE);
    JPanel filter = col(platform, genre, favorite);
    JPopupMenu popup = new JPopupMenu();
    popup.add(filter);
    JButton filters = button("Filtri ▾", () -> popup.show(search, Math.max(0, search.getWidth() - 210), search.getHeight()));
    JPanel sr = row(search, button("Cerca", true, () -> {
    }), filters);
    ((FlowLayout) sr.getLayout()).setAlignment(FlowLayout.CENTER);
    top.add(sr);
    JPanel area = new JPanel(new BorderLayout());
    area.setOpaque(false);
    List < Map < String, Object >> base = provided == null ? new ArrayList < >(rows("games")) : provided;
    String [] selected = {
      "Posseduti"
    };
    Runnable refresh =() -> {
      String q = search.getText().trim().toLowerCase(Locale.ROOT), p = Objects.toString(platform.getSelectedItem()), ge = Objects.toString(genre.getSelectedItem());
      List < Map < String, Object >> found = base.stream().filter(g -> {
        Map < String, Object > e = entry(s(g, "id"));
        String st = s(e, "status");
        boolean match = ! library || switch (selected [0]) {
          case "In corso" -> st.equals("IN_CORSO");
          case "Finiti" -> st.equals("FINITO");
          case "Da giocare" -> st.equals("DA_GIOCARE");
          case "Desiderati" -> st.equals("DESIDERATO");
          default -> st.equals("IN_CORSO") || st.equals("FINITO");
        };
        return match && s(g, "name").toLowerCase(Locale.ROOT).contains(q) &&(p.equals(platforms [0]) || s(g, "platforms").toLowerCase(Locale.ROOT).contains(p.toLowerCase(Locale.ROOT))) &&(ge.equals("Tutti i generi") || s(g, "genres").toLowerCase(Locale.ROOT).contains(ge.toLowerCase(Locale.ROOT))) &&(! favorite.isSelected() || Json.yes(e, "favorite"));
      }).toList();
      area.removeAll();
      area.add(scroll(grid(found, library)));
      area.revalidate();
      area.repaint();
    };
    if (library) {
      JPanel states = row();
      for (String st : List.of("Posseduti", "In corso", "Finiti", "Da giocare", "Desiderati")) states.add(button(st, () -> {
        selected [0] = st;
        refresh.run();
      }));
      top.add(states);
    } else if (mode.equals("catalog")) {
      JPanel sections=row(button("Liste in evidenza",()->lists()),button("Giochi in evidenza",()->loadCatalog("","featured",0)),button("Retro",()->loadCatalog("","retro",0)));((FlowLayout)sections.getLayout()).setAlignment(FlowLayout.CENTER);top.add(sections);
      if (! Json.yes(state, "igdb")) top.add(muted("Catalogo originale: 5 giochi. Configura IGDB per ricerca completa, " + "immagini e piattaforme retro."));
    } else top.add(button("Torna al catalogo", () -> catalog(false, "catalog", null)));
    Runnable query =() -> {
      if (library || mode.equals("list") || provided != null && ! Json.yes(state, "igdb")) {
        refresh.run();
        return;
      }
      if (Json.yes(state, "igdb")) loadCatalog(search.getText(), mode, 0);
      else refresh.run();
    };
    ((JButton) sr.getComponent(1)).addActionListener(e -> query.run());
    search.addActionListener(e -> query.run());
    platform.addActionListener(e -> refresh.run());
    genre.addActionListener(e -> refresh.run());
    favorite.addActionListener(e -> refresh.run());
    content.add(top, BorderLayout.NORTH);
    content.add(area);
    if (! library && provided != null && provided.size() == 36) content.add(button("Altri risultati", () -> loadCatalog(search.getText(), mode, 36)), BorderLayout.SOUTH);
    refresh.run();
    frame.revalidate();
  }
  int catalogOffset = 0;
  String catalogQuery = "";
  void loadCatalog(String query, String mode, int offset) {
    int nextOffset = offset == 0 ? 0 : catalogOffset + 36;
    work("Ricerca nel catalogo esterno…", () -> {
      Map < String, Object > r = api.call("catalog", obj("query", query, "mode", mode, "offset", nextOffset));
      state = api.call("state", obj());
      return r;
    }, r -> {
      catalogOffset = nextOffset;
      catalogQuery = query;
      catalog(false, mode, l(r.get("games")).stream().map(Json::map).toList());
    });
  }
  void home() {
    shell("Home");
    title("La tua Home");
    List < Map < String, Object >> favorites = rows("entries").stream().filter(e -> Json.yes(e, "favorite")).toList();
    Set < String > genres = new HashSet < >();
    for (Map < String, Object > e : favorites) genres.addAll(Arrays.asList(s(game(s(e, "game")), "genres").split(", ")));
    List < Map < String, Object >> rec = rows("games").stream().filter(g -> ! Json.yes(entry(s(g, "id")), "favorite") && s(entry(s(g, "id")), "status").isBlank()).sorted(Comparator.comparingInt((Map < String, Object > g) ->(int) genres.stream().filter(z -> s(g, "genres").contains(z)).count()).reversed()).limit(4).toList();
    JPanel advice = card();
    advice.add(label("Consigli per te", 22, true), BorderLayout.NORTH);
    JPanel covers = new JPanel(new GridLayout(1, 4, 12, 0));
    covers.setOpaque(false);
    for (Map < String, Object > g : rec) covers.add(gameCard(g, false));
    for (int i = rec.size(); i < 4; i ++) covers.add(new JPanel() {
      {
        setOpaque(false);
      }
    });
    advice.add(covers);
    advice.add(muted(favorites.isEmpty() ? "Aggiungi preferiti per personalizzare i consigli." : "In base ai generi dei tuoi preferiti."), BorderLayout.SOUTH);
    JPanel feed = card();
    feed.add(label("Attività degli amici", 22, true), BorderLayout.NORTH);
    JPanel items = col();
    for (Map < String, Object > a : rows("feed").stream().limit(5).toList()) {
      items.add(wrap(s(a, "handle") + " " + s(a, "action") + " " + s(game(s(a, "game")), "name"), 240));
    }
    if (items.getComponentCount() == 0) items.add(wrap("Le attività condivise dagli amici appariranno qui.", 240));
    feed.add(items);
    JPanel upper = new JPanel(new GridBagLayout());
    upper.setOpaque(false);
    GridBagConstraints c = new GridBagConstraints();
    c.fill = GridBagConstraints.BOTH;
    c.weighty = 1;
    c.weightx = 0.72;
    c.gridx = 0;
    c.insets = new Insets(0, 0, 12, 12);
    upper.add(advice, c);
    c.gridx = 1;
    c.weightx = 0.28;
    c.insets = new Insets(0, 0, 12, 0);
    upper.add(feed, c);
    JPanel recent = card();
    recent.add(label("Giochi recenti", 22, true), BorderLayout.NORTH);
    JPanel recentItems = col();
    rows("entries").stream().filter(e -> ! s(e, "status").isBlank()).sorted(Comparator.comparingLong((Map < String, Object > e) -> Json.num(e, "updated")).reversed()).limit(4).forEach(e -> recentItems.add(button(s(game(s(e, "game")), "name") + "  ·  " + stateName(s(e, "status")), () -> detail(game(s(e, "game"))))));
    if (recentItems.getComponentCount() == 0) recentItems.add(muted("Aggiungi il tuo primo gioco dal catalogo."));
    recent.add(recentItems);
    JPanel featured = card();
    featured.add(label("Liste in evidenza", 22, true), BorderLayout.NORTH);
    JPanel listItems = col();
    for (Map < String, Object > ls : rows("lists").stream().limit(3).toList()) listItems.add(button(s(ls, "title"), () -> showList(ls)));
    if (listItems.getComponentCount() == 0) listItems.add(muted("Ancora nessuna lista condivisa."));
    listItems.add(button("Esplora le liste", () -> lists()));
    featured.add(listItems);
    JPanel lower = new JPanel(new GridLayout(1, 2, 16, 0));
    lower.setOpaque(false);
    lower.add(recent);
    lower.add(featured);
    showBody(col(upper, lower));
  }
  void detail(Map < String, Object > g) {
    shell("Catalogo");
    String gid = s(g, "id");
    Map < String, Object > e = entry(gid);
    JPanel all = col();
    all.add(new Art(s(g, "hero").isBlank() ? s(g, "cover") : s(g, "hero"), s(g, "name"), 1200, 210));
    JPanel head = new JPanel(new BorderLayout(22, 5));
    head.setOpaque(false);head.setBorder(new EmptyBorder(0,0,8,0));
    head.add(new Art(s(g, "cover"), s(g, "name"), 165, 230), BorderLayout.WEST);
    JPanel summary = col(label(s(g, "name"), 26, true), muted(s(g, "year") + " · " + s(g, "platforms")), muted(s(g, "genres")), wrap(s(g, "summary"), 480));
    head.add(summary);
    String [] st = {
      "Non in libreria", "In corso", "Finito", "Da giocare", "Desiderato"
    }, codes = {
      "", "IN_CORSO", "FINITO", "DA_GIOCARE", "DESIDERATO"
    };
    JComboBox < String > statusBox = combo(st);
    int index = Arrays.asList(codes).indexOf(s(e, "status"));
    statusBox.setSelectedIndex(Math.max(0, index));
    JCheckBox heart = new JCheckBox("Preferito", Json.yes(e, "favorite"));
    heart.setOpaque(false);
    heart.setForeground(BLUE);
    JSpinner rating = ratingSpinner(e.get("rating") instanceof Number ? ((Number)e.get("rating")).doubleValue() : 8.0);
    JCheckBox rated = new JCheckBox("Il tuo voto / 10", e.get("rating") != null);
    rated.setOpaque(false);
    rated.setForeground(BLUE);
    JPanel controls = col(row(button("+ Libreria", true, () -> saveEntry(g, "IN_CORSO", heart.isSelected(), rated.isSelected() ? rating.getValue() : null, () -> detail(g))), heart), label("Stato", 14, true), statusBox, row(rated, rating), button("Salva", true, () -> saveEntry(g, codes [statusBox.getSelectedIndex()], heart.isSelected(), rated.isSelected() ? rating.getValue() : null, () -> detail(g))));
    Map < String, Object > hours = steamHours(gid, rows("links"));
    controls.add(muted(hours == null ? "Ore giocate: Non disponibile" : String.format(Locale.ITALY, "Steam · %.1f ore", Json.num(hours, "minutes") / 60.0)));
    if (! s(g, "steamApp").isBlank()) controls.add(button("Trofei / obiettivi Steam", () -> achievements(g, uid(), null)));
    else controls.add(muted("Trofei: nessun ID piattaforma collegato"));
    controls.setPreferredSize(new Dimension(330, 320));
    head.add(controls, BorderLayout.EAST);
    all.add(head);
    JPanel reviewBox = card();
    reviewBox.add(label("La tua recensione", 20, true), BorderLayout.NORTH);
    JTextArea text = new JTextArea(3, 60);
    text.setLineWrap(true);
    text.setWrapStyleWord(true);
    text.setBackground(LAV);
    text.setForeground(BLUE);
    text.setBorder(new EmptyBorder(12, 12, 12, 12));
    reviewBox.add(textBox(text));
    reviewBox.add(button("Pubblica / aggiorna", true, () -> mutate("review", obj("game", gid, "text", text.getText()), () -> detail(g))), BorderLayout.SOUTH);
    all.add(reviewBox);
    JPanel reviews = card();
    reviews.add(label("Recensioni della community", 20, true), BorderLayout.NORTH);
    JPanel reviewList = col(muted("Caricamento…"));
    reviews.add(reviewList);
    all.add(reviews);
    showBody(all);
    work("Caricamento recensioni…", () -> api.call("reviews", obj("game", gid)), r -> {
      reviewList.removeAll();
      for (Object o : l(r.get("reviews"))) {
        Map < String, Object > v = m(o);
        if (s(v, "user").equals(uid())) text.setText(s(v, "text"));
        JPanel review = col(label(s(v, "handle") +(v.get("rating") == null ? "" : "   Voto " + v.get("rating") + " / 10"), 15, true), wrap(s(v, "text"), 820));
        if (! s(v, "user").equals(uid())) review.add(row(button("Like " + Json.num(v, "likes"), () -> mutate("vote", obj("review", v.get("id"), "value", Json.num(v, "myVote") == 1 ? 0 : 1), () -> detail(g))), button("Dislike " + Json.num(v, "dislikes"), () -> mutate("vote", obj("review", v.get("id"), "value", Json.num(v, "myVote") == - 1 ? 0 : - 1), () -> detail(g)))));
        reviewList.add(review);
      }
      if (reviewList.getComponentCount() == 0) reviewList.add(muted("Nessuna recensione. Puoi scrivere la prima."));
      reviewList.revalidate();
      frame.repaint();
    });
  }
  Map < String, Object > steamHours(String gid, List < Map < String, Object >> links) {
    for (Map < String, Object > link : links) for (Object o : l(link.get("games"))) if (s(m(o), "game").equals(gid)) return m(o);
    return null;
  }
  void friends() {
    shell("Amici");
    JPanel top = col(label("Amici", 30, true));
    JTextField search = field("nomeutente#ID");
    search.setColumns(34);
    Runnable find =() -> work("Ricerca utente…", () -> api.call("find-user", obj("handle", search.getText())), r -> {
      Map < String, Object > u = m(r.get("user"));
      if (u.isEmpty()) {
        JOptionPane.showMessageDialog(frame, "Nessun utente con questo identificativo esatto.");
        return;
      }
      Object [] opts = {
        "Invia richiesta", "Apri profilo", "Annulla"
      };
      int n = JOptionPane.showOptionDialog(frame, s(u, "handle"), "Utente trovato", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, opts, opts [0]);
      if (n == 0) mutate("friend", obj("user", u.get("id")), () -> friends());
      if (n == 1) profile(s(u, "id"));
    });
    search.addActionListener(e -> find.run());
    top.add(row(search, button("Cerca", true, find), button("Copia il tuo ID", () -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(s(m(state.get("me")), "handle")), null))));
    top.add(muted("Il tuo identificativo: " + s(m(state.get("me")), "handle")));
    content.add(top, BorderLayout.NORTH);
    JPanel mine = card();
    mine.add(label("I tuoi amici", 22, true), BorderLayout.NORTH);
    JPanel members = col();
    for (Map < String, Object > u : rows("friends")) {
      JPanel card = col(label(s(u, "handle"), 18, true), muted(s(u, "visibility").equals("private") ? "Libreria privata" : "Visibilità: " +(s(u, "visibility").equals("friends") ? "solo amici" : "pubblica")), row(button("Profilo", () -> profile(s(u, "id"))), button("Confronta", true, () -> compare(s(u, "id")))));
      members.add(card);
    }
    if (members.getComponentCount() == 0) members.add(wrap("Cerca un utente tramite nome e ID per inviare una richiesta.", 400));
    mine.add(members);
    JPanel requests = card();
    requests.add(label("Richieste di amicizia", 22, true), BorderLayout.NORTH);
    JPanel req = col();
    for (Map < String, Object > r : rows("requests")) {
      if (! s(r, "status").equals("pending")) continue;
      Map < String, Object > u = m(r.get("other"));
      JPanel v = col(label(s(u, "handle"), 16, true), muted(Json.yes(r, "incoming") ? "Ricevuta" : "In attesa"));
      if (Json.yes(r, "incoming")) v.add(row(button("Accetta", true, () -> mutate("friend-action", obj("request", r.get("id"), "action", "accept"), () -> friends())), button("Rifiuta", () -> mutate("friend-action", obj("request", r.get("id"), "action", "remove"), () -> friends()))));
      else v.add(button("Annulla", () -> mutate("friend-action", obj("request", r.get("id"), "action", "remove"), () -> friends())));
      req.add(v);
    }
    if (req.getComponentCount() == 0) req.add(muted("Nessuna richiesta in sospeso."));
    requests.add(req);
    JPanel body = new JPanel(new GridLayout(1, 2, 18, 0));
    body.setOpaque(false);
    body.add(mine);
    body.add(requests);
    showBody(body);
  }
  void profile(String target) {
    work("Caricamento profilo…", () -> api.call("profile", obj("user", target)), r -> {
      shell("Amici");
      Map < String, Object > u = m(r.get("user"));
      title(s(u, "handle"));
      JPanel body = col(row(button("Confronta statistiche e trofei", true, () -> compare(target)), button("Torna agli amici", () -> friends())));
      if (! Json.yes(r, "visible")) body.add(label("Questa libreria è privata", 20, true));
      else {
        List < Map < String, Object >> items = l(r.get("entries")).stream().map(Json::map).filter(e -> Set.of("FINITO", "IN_CORSO").contains(s(e, "status"))).map(e -> game(s(e, "game"))).toList();
        body.add(grid(items, false));
      }
      showBody(body);
    });
  }
  List < Map < String, Object >> owned(List < Map < String, Object >> entries) {
    return entries.stream().filter(e -> Set.of("FINITO", "IN_CORSO").contains(s(e, "status"))).toList();
  }
  double average(List < Map < String, Object >> entries) {
    return entries.stream().filter(e -> e.get("rating") instanceof Number).mapToDouble(e ->((Number) e.get("rating")).doubleValue()).average().orElse(Double.NaN);
  }
  Map < String, Double > counts(List < Map < String, Object >> entries, String field) {
    Map < String, Double > out = new LinkedHashMap < >();
    for (Map < String, Object > e : entries) {
      String v = field.equals("status") ? stateName(s(e, "status")) : s(game(s(e, "game")), field);
      for (String t : v.split(", ")) if (! t.isBlank()) out.merge(t, 1.0, Double::sum);
    }
    return out;
  }
  JPanel metric(String title, String value) {
    JPanel p = new RoundPanel(LAV, 22);
    p.setBorder(new EmptyBorder(14, 18, 14, 18));
    p.setLayout(new BorderLayout(8, 8));
    p.add(label(title, 14, true), BorderLayout.NORTH);
    p.add(label(value, 28, true));
    return p;
  }
  JPanel chart(String title, Map < String, Double > data, boolean donut) {
    JPanel p = card();
    p.add(label(title, 18, true), BorderLayout.NORTH);
    p.add(new Chart(data, donut));
    return p;
  }
  void stats() {
    shell("Statistiche");
    JPanel top = col(label("Le tue statistiche", 30, true), row(muted("Totali attuali della libreria"), button("Confronta con un utente", () -> chooseComparison()), button("Classifica mensile delle ore", () -> ranking())));
    content.add(top, BorderLayout.NORTH);
    List < Map < String, Object >> entries = owned(rows("entries"));
    long finished = entries.stream().filter(e -> s(e, "status").equals("FINITO")).count();
    double avg = average(rows("entries"));
    JPanel metrics = new JPanel(new GridLayout(1, 4, 12, 0));
    metrics.setOpaque(false);
    metrics.add(metric("Giochi posseduti", "" + entries.size()));
    metrics.add(metric("Giochi finiti", "" + finished));
    metrics.add(metric("Completamento", entries.isEmpty() ? "—" : Math.round(finished * 100.0 / entries.size()) + "%"));
    metrics.add(metric("Voto medio", Double.isNaN(avg) ? "—" : String.format(Locale.ITALY, "%.1f / 10", avg)));
    JPanel charts = new JPanel(new GridLayout(2, 2, 16, 16));
    charts.setOpaque(false);
    charts.add(chart("Stato della libreria", counts(entries, "status"), true));
    charts.add(chart("Generi più presenti", counts(entries, "genres"), false));
    charts.add(chart("Giochi per piattaforma (catalogo)", counts(entries, "platforms"), false));
    Map < String, Double > votes = new LinkedHashMap < >();
    for (int i = 0; i <= 10; i ++) votes.put("" + i, 0.0);
    for (Map < String, Object > e : rows("entries")) if (e.get("rating") instanceof Number n) votes.merge("" + Math.round(n.doubleValue()), 1.0, Double::sum);
    charts.add(chart("Distribuzione dei voti (arrotondati)", votes, false));
    JPanel linked = card();
    linked.add(label("Ore e obiettivi sincronizzati", 22, true), BorderLayout.NORTH);
    JPanel linkData = col();
    if (rows("links").isEmpty()) {
      linkData.add(muted("Collega un account compatibile per importare ore e obiettivi."));
      linkData.add(button("Gestisci collegamenti", true, () -> settings()));
    } else {
      Map < String, Object > link = rows("links").get(0);
      long minutes = l(link.get("games")).stream().map(Json::map).mapToLong(g -> Json.num(g, "minutes")).sum();
      linkData.add(label(String.format(Locale.ITALY, "Steam · %.1f ore complessive", minutes / 60.0), 20, true));
      Map < String, Object > cache = m(link.get("trophies"));
      long unlocked = 0, total = 0;
      for (Object v : cache.values()) for (Object a : l(v)) {
        total ++;
        if (Json.yes(m(a), "unlocked")) unlocked ++;
      }
      linkData.add(label("Obiettivi importati: " + unlocked + " / " + total + "  ·  " + cache.size() + " giochi sincronizzati", 18, true));
      linkData.add(muted("Conteggio parziale finché non sincronizzi tutti i giochi. Nessun dato " + "PlayStation/Xbox importato."));
      linkData.add(row(button("Sincronizza ore", true, () -> syncSteam()), button("Sincronizza obiettivi dei giochi giocati", () -> syncTrophies()), button("Apri un gioco Steam", () -> catalog(true, "library", null))));
      Map < String, Double > hours = new LinkedHashMap < >();
      l(link.get("games")).stream().map(Json::map).sorted(Comparator.comparingLong((Map < String, Object > g) -> Json.num(g, "minutes")).reversed()).limit(6).forEach(g -> hours.put(s(game(s(g, "game")), "name"), Json.num(g, "minutes") / 60.0));
      linkData.add(chart("Ore per gioco · Steam", hours, false));
    }
    linked.add(linkData);
    showBody(col(metrics, charts, linked));
  }
  void chooseComparison() {
    List < Map < String, Object >> friends = rows("friends");
    if (friends.isEmpty()) {
      JOptionPane.showMessageDialog(frame, "Aggiungi un amico oppure cerca un utente nella pagina Amici.");
      return;
    }
    String [] handles = friends.stream().map(u -> s(u, "handle")).toArray(String []::new);
    String h =(String) JOptionPane.showInputDialog(frame, "Scegli un giocatore", "Confronto", JOptionPane.PLAIN_MESSAGE, null, handles, handles [0]);
    if (h != null) friends.stream().filter(u -> s(u, "handle").equals(h)).findFirst().ifPresent(u -> compare(s(u, "id")));
  }
  void compare(String target) {
    work("Caricamento confronto…", () -> api.call("profile", obj("user", target)), r -> {
      shell("Amici");
      title("Confronta giocatori");
      Map < String, Object > other = m(r.get("user"));
      JPanel all = col(label(s(m(state.get("me")), "handle") + "   vs   " + s(other, "handle"), 24, true));
      if (! Json.yes(r, "visible")) {
        all.add(label("La libreria di questo utente è privata", 20, true));
        showBody(all);
        return;
      }
      JPanel filters = row(label("Piattaforme e console", 16, true));
      JCheckBox allPlatforms = new JCheckBox("Tutte", true), steam = new JCheckBox("Steam", true);
      for (JCheckBox c : List.of(allPlatforms, steam)) {
        c.setOpaque(false);
        c.setForeground(BLUE);
        filters.add(c);
      }
      for (String name : List.of("PS5", "PS4", "Xbox")) {
        JCheckBox c = new JCheckBox(name);
        c.setOpaque(false);
        c.setEnabled(false);
        c.setToolTipText("Integrazione non disponibile in questa versione");
        filters.add(c);
      }
      allPlatforms.addActionListener(e -> steam.setSelected(allPlatforms.isSelected()));
      steam.addActionListener(e -> allPlatforms.setSelected(steam.isSelected()));
      all.add(filters);
      all.add(muted("Al momento è disponibile Steam. Le altre console saranno selezionabili " + "quando integrate."));
      List < Map < String, Object >> mine = owned(rows("entries")), theirs = owned(l(r.get("entries")).stream().map(Json::map).toList());
      if (Json.yes(r, "statsAllowed")) {
        JPanel metric = new JPanel(new GridLayout(1, 2, 16, 0));
        metric.setOpaque(false);
        metric.add(metric("Tu · posseduti / finiti", mine.size() + " / " + mine.stream().filter(e -> s(e, "status").equals("FINITO")).count()));
        metric.add(metric(s(other, "username") + " · posseduti / finiti", theirs.size() + " / " + theirs.stream().filter(e -> s(e, "status").equals("FINITO")).count()));
        all.add(metric);
        double a = average(rows("entries")), b = average(l(r.get("entries")).stream().map(Json::map).toList());
        all.add(label("Voto medio: " +(Double.isNaN(a) ? "—" : String.format(Locale.ITALY, "%.1f", a)) + "   /   " +(Double.isNaN(b) ? "—" : String.format(Locale.ITALY, "%.1f", b)), 18, true));
      } else all.add(muted("Questo utente non condivide le statistiche."));
      JPanel trophies = col(label("Obiettivi Steam · giochi in comune", 22, true));
      if (! Json.yes(r, "trophiesAllowed")) {
        trophies.add(muted("Questo utente non condivide i trofei."));
      } else {
        List < Map < String, Object >> theirLinks = l(r.get("links")).stream().map(Json::map).toList();
        Set < String > theirGames = new HashSet < >();
        for (Map < String, Object > li : theirLinks) for (Object g : l(li.get("games"))) theirGames.add(s(m(g), "game"));
        List < Map < String, Object >> shared = new ArrayList < >();
        for (Map < String, Object > li : rows("links")) for (Object x : l(li.get("games"))) {
          Map < String, Object > a = m(x);
          if (theirGames.contains(s(a, "game"))) shared.add(a);
        }
        for (Map < String, Object > x : shared) {
          Map < String, Object > g = game(s(x, "game"));
          Map < String, Object > hh = steamHours(s(g, "id"), theirLinks);
          String hours = Json.yes(r, "statsAllowed") ? String.format(Locale.ITALY, " · %.1f h / %.1f h", Json.num(x, "minutes") / 60.0, hh == null ? 0 : Json.num(hh, "minutes") / 60.0) : "";
          trophies.add(row(label(s(g, "name") + hours, 15, true), button("Confronta obiettivi", () -> {
            if (steam.isSelected()) achievements(g, uid(), target);
            else JOptionPane.showMessageDialog(frame, "Seleziona almeno una piattaforma.");
          })));
        }
        if (shared.isEmpty()) trophies.add(muted("Nessun gioco Steam comune sincronizzato, oppure account non collegati."));
      }
      JPanel trophyPanel = card();
      trophyPanel.add(trophies);
      all.add(trophyPanel);
      all.add(muted("Sono mostrati soltanto i dati condivisi dall'altro giocatore."));
      showBody(all);
    });
  }
  void achievements(Map < String, Object > g, String owner, String other) {
    String app = s(g, "steamApp");
    if (app.isBlank()) {
      error(new IllegalArgumentException("Nessun identificativo Steam associato"));
      return;
    }
    work("Importazione obiettivi Steam…", () -> {
      Map < String, Object > a = api.call("achievements", obj("app", app, "user", owner));
      if (other != null) a.put("comparison", api.call("achievements", obj("app", app, "user", other)).get("achievements"));
      state = api.call("state", obj());
      return a;
    }, r -> {
      JDialog dialog = new JDialog(frame, other == null ? "Obiettivi · " + s(g, "name") : "Confronto obiettivi · " + s(g, "name"), true);
      JPanel body = col(label(s(g, "name") + " · Steam", 22, true));
      Map < String, Map < String, Object >> compare = new HashMap < >();
      for (Object o : l(r.get("comparison"))) compare.put(s(m(o), "id"), m(o));
      int unlocked = 0;
      for (Object o : l(r.get("achievements"))) {
        Map < String, Object > a = m(o);
        if (Json.yes(a, "unlocked")) unlocked ++;
        String marker = Json.yes(a, "unlocked") ? "Ottenuto" : "Da sbloccare";
        if (other != null) {
          Map < String, Object > b = compare.get(s(a, "id"));
          marker = "Tu: " +(Json.yes(a, "unlocked") ? "Ottenuto" : "Da sbloccare") + "    Altro: " +(b == null ? "Non disponibile" : Json.yes(b, "unlocked") ? "Ottenuto" : "Da sbloccare");
        }
        JPanel row = col(label(s(a, "name"), 16, true), wrap(s(a, "description"), 620), muted(marker));
        body.add(row);
      }
      body.add(label(unlocked + " / " + l(r.get("achievements")).size() + " ottenuti", 18, true), 0);
      body.setBorder(new EmptyBorder(24, 24, 24, 24));
      dialog.add(scroll(body));
      dialog.setSize(820, 650);
      dialog.setLocationRelativeTo(frame);
      dialog.setVisible(true);
    });
  }
  void syncSteam() {
    work("Sincronizzazione Steam…", () -> {
      Map < String, Object > r = api.call("steam-sync", obj());
      state = api.call("state", obj());
      return r;
    }, r -> {
      JOptionPane.showMessageDialog(frame, Json.num(r, "count") +(" giochi sincronizzati. I giochi Steam sono consultabili nel catalogo; " + "aggiungili alla libreria con lo stato che preferisci."));
      stats();
    });
  }
  void syncTrophies() {
    List < Map < String, Object >> games = new ArrayList < >();
    for (Map < String, Object > link : rows("links")) for (Object o : l(link.get("games"))) {
      Map < String, Object > g = m(o);
      if (Json.num(g, "minutes") > 0) games.add(game(s(g, "game")));
    }
    if (games.isEmpty()) {
      JOptionPane.showMessageDialog(frame, "Sincronizza prima le ore dei giochi Steam.");
      return;
    }
    if (JOptionPane.showConfirmDialog(frame, "Verranno interrogati " + games.size() + " giochi. Può richiedere diversi minuti. Continuare?", "Sincronizzazione obiettivi", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
    work("Sincronizzazione obiettivi…", () -> {
      int ok = 0, failed = 0;
      for (int i = 0; i < games.size(); i ++) {
        int current = i + 1;
        SwingUtilities.invokeLater(() -> status.setText("Obiettivi: " + current + " / " + games.size()));
        try {
          api.call("achievements", obj("app", games.get(i).get("steamApp"), "user", uid()));
          ok ++;
        } catch (Exception e) {
          failed ++;
        }
      }
      state = api.call("state", obj());
      return new int [] {
        ok, failed
      };
    }, r -> {
      JOptionPane.showMessageDialog(frame, r [0] + " giochi aggiornati; " + r [1] +(" non disponibili (nessun obiettivo, profilo privato o errore del " + "servizio)."));
      stats();
    });
  }
  void ranking() {
    work("Caricamento classifica…", () -> api.call("ranking", obj()), r -> {
      shell("Statistiche");
      title("Ore osservate · " + s(r, "month"));
      JPanel body = col(wrap(s(r, "note"), 950));
      Map < String, Double > data = new LinkedHashMap < >();
      for (Object o : l(r.get("ranking"))) {
        Map < String, Object > v = m(o);
        data.put(s(game(s(v, "game")), "name"), Json.num(v, "minutes") / 60.0);
      }
      body.add(chart("Ore sincronizzate nel mese (UTC)", data, false));
      body.add(muted("La prima sincronizzazione crea il punto iniziale: le ore pregresse non " + "vengono attribuite al mese."));
      showBody(body);
    });
  }
  void lists() {
    shell("Catalogo");
    JPanel top = row(label("Liste in evidenza", 28, true), button("Crea lista", true, () -> createList()));
    content.add(top, BorderLayout.NORTH);
    JPanel body = col();
    for (Map < String, Object > ls : rows("lists")) {
      JPanel line = row(button(s(ls, "title") + " · " + s(ls, "handle"), () -> showList(ls)));
      if (s(ls, "user").equals(uid())) line.add(button("Elimina", () -> {
        if (JOptionPane.showConfirmDialog(frame, "Eliminare questa lista?", "Lista", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) mutate("list-delete", obj("id", ls.get("id")), () -> lists());
      }));
      body.add(line);
    }
    if (rows("lists").isEmpty()) body.add(muted("Non ci sono ancora liste. Puoi crearne una e scegliere se renderla pubblica."));
    showBody(body);
  }
  void showList(Map < String, Object > ls) {
    List < Map < String, Object >> g = l(ls.get("games")).stream().map(Object::toString).map(this::game).toList();
    catalog(false, "list", g);
    content.add(label(s(ls, "title") + " · " + s(ls, "handle"), 18, true), BorderLayout.SOUTH);
    frame.revalidate();
  }
  void createList() {
    JTextField title = field("Titolo della lista");
    JCheckBox publicList = new JCheckBox("Rendi pubblica questa lista");
    publicList.setOpaque(false);
    List < Map < String, Object >> games = rows("games");
    DefaultListModel < String > model = new DefaultListModel < >();
    games.forEach(g -> model.addElement(s(g, "name")));
    JList < String > select = new JList < >(model);
    select.setVisibleRowCount(8);
    JPanel panel = col(label("Titolo", 14, true), title, new JScrollPane(select), muted("Ctrl + clic per selezionare più giochi"), publicList);
    if (JOptionPane.showConfirmDialog(frame, panel, "Crea lista", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
      List < Object > ids = new ArrayList < >();
      for (int i : select.getSelectedIndices()) ids.add(games.get(i).get("id"));
      mutate("list-create", obj("title", title.getText(), "games", ids, "public", publicList.isSelected()), () -> lists());
    }
  }
  void settings() {
    shell("Impostazioni");
    title("Impostazioni");
    Map < String, Object > me = m(state.get("me"));
    JPanel account = card();
    account.add(label("Account", 22, true), BorderLayout.NORTH);
    account.add(col(label(s(me, "handle"), 20, true), muted(s(me, "email")), button("Modifica nome utente", () -> {
      String name = JOptionPane.showInputDialog(frame, "Nome utente", s(me, "username"));
      if (name != null) mutate("profile-update", obj("username", name), () -> settings());
    }), button("Cambia password", () -> password()), button("Importa libreria originale (.txt)", () -> importLegacy())));
    JPanel privacy = card();
    privacy.add(label("Privacy", 22, true), BorderLayout.NORTH);
    JComboBox < String > visibility = combo(new String [] {
      "Privata", "Solo amici", "Pubblica"
    });
    visibility.setSelectedIndex(s(me, "visibility").equals("friends") ? 1 : s(me, "visibility").equals("public") ? 2 : 0);
    JCheckBox stats = new JCheckBox("Condividi statistiche", Json.yes(me, "shareStats")), trophies = new JCheckBox("Condividi trofei", Json.yes(me, "shareTrophies")), activity = new JCheckBox("Mostra attività agli amici", Json.yes(me, "shareActivity"));
    for (JCheckBox c : List.of(stats, trophies, activity)) {
      c.setOpaque(false);
      c.setForeground(BLUE);
    }
    privacy.add(col(label("Visibilità libreria", 14, true), visibility, stats, trophies, activity, button("Salva privacy", true, () -> mutate("settings", obj("visibility", new String [] {
      "private", "friends", "public"
    }
    [visibility.getSelectedIndex()], "shareStats", stats.isSelected(), "shareTrophies", trophies.isSelected(), "shareActivity", activity.isSelected()), () -> settings()))));
    JPanel links = card();
    links.add(label("Account collegati", 22, true), BorderLayout.NORTH);
    JPanel lp = col();
    if (rows("links").isEmpty()) {
      lp.add(wrap("Steam: importazione ore e obiettivi tramite autenticazione nel browser.", 400));
      lp.add(button("Collega Steam", true, () -> work("Preparazione collegamento…", () -> api.call("steam-start", obj()), r -> {
        try {
          Desktop.getDesktop().browse(java.net.URI.create(s(r, "url")));
          JOptionPane.showMessageDialog(frame, "Completa l'accesso su Steam nel browser, poi premi Aggiorna collegamenti.");
        } catch (Exception e) {
          error(e);
        }
      })));
    } else {
      lp.add(label("Steam · " + s(rows("links").get(0), "steamId"), 15, true));
      lp.add(row(button("Sincronizza ore", true, () -> syncSteam()), button("Scollega", () -> {
        if (JOptionPane.showConfirmDialog(frame, "Rimuovere collegamento, ore e obiettivi importati? I giochi in libreria " + "rimangono.", "Steam", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) mutate("steam-unlink", obj(), () -> settings());
      })));
    }
    lp.add(button("Aggiorna collegamenti", () -> reload(() -> settings())));
    lp.add(muted("PlayStation, Xbox e Nintendo: integrazioni non disponibili."));
    lp.add(muted(Json.yes(state, "steam") ? "Steam Web API configurata" : "Chiave Steam Web API da configurare sul server"));
    links.add(lp);
    JPanel accessibility = card();
    accessibility.add(label("Lingua e accessibilità", 22, true), BorderLayout.NORTH);
    JComboBox < String > lang = combo(new String [] {
      "Italiano"
    });
    JComboBox < String > size = combo(new String [] {
      "Standard", "Grande"
    });
    size.setSelectedIndex(Theme.scale > 1 ? 1 : 0);
    JCheckBox contrast = new JCheckBox("Contrasto elevato", Theme.contrast);
    contrast.setOpaque(false);
    accessibility.add(col(label("Lingua", 14, true), lang, label("Dimensione testo", 14, true), size, contrast, muted("L'interfaccia non usa animazioni."), button("Applica", true, () -> {
      Theme.scale = size.getSelectedIndex() == 1 ? 1.15f : 1;
      Theme.contrast = contrast.isSelected();
      prefs.putFloat("textScale", Theme.scale);
      prefs.putBoolean("contrast", Theme.contrast);
      Theme.setup();
      settings();
    })));
    JPanel cards = new JPanel(new GridLayout(2, 2, 18, 18));
    cards.setOpaque(false);
    cards.add(account);
    cards.add(privacy);
    cards.add(links);
    cards.add(accessibility);
    JPanel bottom = row(button("Esporta i tuoi dati", () -> export()), button("Aggiorna", () -> reload(() -> settings())), button("Disconnetti", () -> work("Disconnessione…", () -> api.call("logout", obj()), r -> {
      api.token = "";
      state = obj();
      auth(false);
    })), button("Esci dall'app", () -> frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING))));
    showBody(col(cards, bottom));
  }
  void resetPassword(String initial) {
    JTextField email = field("Email");
    email.setText(initial);
    if (JOptionPane.showConfirmDialog(frame, col(label("Email dell'account", 14, true), email), "Recupero password", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
    work("Invio del codice…", () -> api.call("reset-request", obj("email", email.getText())), r -> {
      JTextField code = field("Codice ricevuto");
      JPasswordField pw = passwordField(24), confirm = passwordField(24);
      JPanel p = col(wrap(s(r, "message"), 350), label("Codice ricevuto per email", 14, true), code, label("Nuova password (almeno 10 caratteri)", 14, true), pw, label("Conferma password", 14, true), confirm);
      if (JOptionPane.showConfirmDialog(frame, p, "Reimposta password", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
        if (! Arrays.equals(pw.getPassword(), confirm.getPassword())) {
          error(new IllegalArgumentException("Le password non coincidono"));
          return;
        }
        work("Reimpostazione…", () -> api.call("reset-confirm", obj("email", email.getText(), "code", code.getText().trim(), "password", new String(pw.getPassword()))), v -> JOptionPane.showMessageDialog(frame, "Password aggiornata. Puoi accedere."));
      }
    });
  }
  void password() {
    JPasswordField old = passwordField(22), next = passwordField(22), confirm = passwordField(22);
    JPanel p = col(label("Password attuale", 14, true), old, label("Nuova password (almeno 10 caratteri)", 14, true), next, label("Conferma nuova password", 14, true), confirm);
    if (JOptionPane.showConfirmDialog(frame, p, "Cambia password", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
      if (! Arrays.equals(next.getPassword(), confirm.getPassword())) {
        error(new IllegalArgumentException("Le password non coincidono"));
        return;
      }
      mutate("password", obj("old", new String(old.getPassword()), "new", new String(next.getPassword())), () -> JOptionPane.showMessageDialog(frame, "Password aggiornata. Le altre sessioni sono state chiuse."));
    }
  }
  void export() {
    JFileChooser chooser = new JFileChooser();
    chooser.setSelectedFile(new java.io.File("gamebacklog-export.json"));
    if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return;
    Path file = chooser.getSelectedFile().toPath();
    if (Files.exists(file) && JOptionPane.showConfirmDialog(frame, "Sostituire il file esistente?", "Esportazione", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
    work("Esportazione…", () -> {
      Files.writeString(file, Json.write(api.call("export", obj())));
      return true;
    }, r -> JOptionPane.showMessageDialog(frame, "Dati esportati."));
  }
  void importLegacy() {
    JFileChooser chooser = new JFileChooser();
    if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return;
    Path file = chooser.getSelectedFile().toPath();
    work("Importazione…", () -> {
      int imported = 0, skipped = 0;
      for (String line : Files.readAllLines(file)) {
        if (line.isBlank()) continue;
        try {
          String [] v = line.split(";", - 1);
          if (v.length != 4) throw new IllegalArgumentException();
          Map < String, Object > g = rows("games").stream().filter(x -> s(x, "name").equalsIgnoreCase(v [0])).findFirst().orElse(null);
          if (g == null) {
            skipped ++;
            continue;
          }
          api.call("entry", obj("game", g.get("id"), "rating", v [1].equals("null") ? null : Double.valueOf(v [1]), "favorite", Boolean.parseBoolean(v [2]), "status", v [3]));
          imported ++;
        } catch (Exception e) {
          skipped ++;
        }
      }
      state = api.call("state", obj());
      return new int [] {
        imported, skipped
      };
    }, r -> {
      JOptionPane.showMessageDialog(frame, r [0] + " giochi importati; " + r [1] + " righe non importate. Il file originale è rimasto intatto.");
      settings();
    });
  }
  static class Chart extends JComponent {
    final Map < String, Double > data;
    final boolean donut;
    Chart(Map < String, Double > d, boolean donut) {
      this.data = d;
      this.donut = donut;
      setPreferredSize(new Dimension(450, 240));
      setMinimumSize(new Dimension(300, 220));
      getAccessibleContext();
      setToolTipText(d.toString());
    }
    protected void paintComponent(Graphics g) {
      Graphics2D a =(Graphics2D) g.create();
      a.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      double sum = data.values().stream().mapToDouble(Double::doubleValue).sum();
      if (sum == 0) {
        a.setFont(font(14, false));
        a.setColor(MUTED);
        a.drawString("Nessun dato disponibile", 20, 80);
        a.dispose();
        return;
      }
      Color [] colors = {
        PINK, PURPLE, new Color(49, 137, 255), new Color(255, 204, 65), new Color(126, 93, 248)
      };
      a.setFont(font(12, true));
      if (donut) {
        int diameter = Math.min(getHeight() - 35, 190), x = 15, y = 15;
        double start = 90;
        int i = 0;
        for (var e : data.entrySet()) {
          double angle = e.getValue() / sum * 360;
          a.setColor(colors [i ++ % colors.length]);
          a.fill(new java.awt.geom.Arc2D.Double(x, y, diameter, diameter, start, - angle, java.awt.geom.Arc2D.PIE));
          start -= angle;
        }
        a.setColor(Color.WHITE);
        a.fillOval(x + diameter / 4, y + diameter / 4, diameter / 2, diameter / 2);
        int yy = 45;
        i = 0;
        for (var e : data.entrySet()) {
          a.setColor(colors [i ++ % colors.length]);
          a.fillOval(diameter + 35, yy - 10, 10, 10);
          a.setColor(BLUE);
          a.drawString(e.getKey() + " · " + String.format(Locale.ITALY, "%.0f", e.getValue()), diameter + 52, yy);
          yy += 30;
        }
      } else {
        List < Map.Entry < String, Double >> items = data.entrySet().stream().sorted(Map.Entry.< String, Double > comparingByValue().reversed()).limit(7).toList();
        double max = items.stream().mapToDouble(Map.Entry::getValue).max().orElse(1);
        int y = 20, i = 0;
        for (var e : items) {
          String name = e.getKey();
          if (name.length() > 19) name = name.substring(0, 18) + "…";
          a.setColor(BLUE);
          a.drawString(name, 5, y + 12);
          int x = 155, w = Math.max(40, getWidth() - 215), bar =(int)(e.getValue() / Math.max(1, max) * w);
          a.setColor(LAV);
          a.fillRoundRect(x, y, w, 18, 12, 12);
          a.setColor(colors [i ++ % colors.length]);
          a.fillRoundRect(x, y, bar, 18, 12, 12);
          a.setColor(BLUE);
          a.drawString(String.format(Locale.ITALY, e.getValue() % 1 == 0 ? "%.0f" : "%.1f", e.getValue()), x + w + 8, y + 13);
          y += 29;
        }
      }
      a.dispose();
    }
  }
  public static void main(String [] args) throws Exception {
    System.setProperty("awt.useSystemAAFontSettings", "on");
    System.setProperty("swing.aatext", "true");
    String base = System.getProperty("gbm.server", System.getenv().getOrDefault("GBM_SERVER_URL", "http://127.0.0.1:8787"));
    Api api = new Api(base);
    if (base.equals("http://127.0.0.1:8787")) {
      try {
        api.call("health", obj());
      } catch (Exception unavailable) {
        Properties cfg = new Properties();
        Path file = Path.of("server.properties");
        if (Files.exists(file)) try (var r = Files.newBufferedReader(file)) {
          cfg.load(r);
        }
        local = new Server(Path.of(cfg.getProperty("DATA_FILE", "data/database.json")), 8787, cfg);
        local.start();
      }
    }
    SwingUtilities.invokeLater(() -> {
      App app = new App(api);
      app.frame.setVisible(true);
    });
  }
}
