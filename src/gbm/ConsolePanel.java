package gbm;

import javax.swing.*;
import java.awt.*;
import java.nio.file.*;
import java.net.URI;
import java.util.*;
import java.util.List;
import static gbm.Theme.*;
import static gbm.Json.*;

/** User-facing setup and private history for the initial console adapters. */
final class ConsolePanel {
  final App app;
  ConsolePanel(App app){this.app=app;}
  JPanel panel(){
    JPanel result=col(label("Console · anteprima",20,true));
    if(!yes(app.state,"consoleSupport")){result.add(App.wrap("Il server deve essere aggiornato alla versione 2.8 per attivare le integrazioni console.",850));return result;}
    for(String platform:List.of("PlayStation","Xbox","Nintendo")){
      Map<String,Object> link=app.rows("consoleLinks").stream().filter(v->str(v,"platform").equals(platform)).findFirst().orElse(Map.of());
      JPanel box=card();JPanel body=col(label(platform,18,true));
      if(platform.equals("PlayStation")){
        body.add(App.wrap("Collegamento sperimentale non ufficiale: importa titoli e conteggio dei trofei. La sessione va ricollegata quando scade o dopo il riavvio del server.",780));
        body.add(button(yes(link,"connected")?"Ricollega PlayStation":"Collega PlayStation",()->psn()));
      }else if(platform.equals("Xbox")){
        body.add(App.wrap("Accesso nel browser Microsoft e importazione dei titoli con progressi negli obiettivi. Non rappresenta la libreria completa dei giochi posseduti.",780));
        JButton connect=button("Collega Xbox",()->startXbox());connect.setEnabled(yes(app.state,"xboxConfigured"));body.add(connect);
        if(!yes(app.state,"xboxConfigured"))body.add(muted("Collegamento Xbox da configurare sul server."));
      }else{
        body.add(App.wrap("Importazione manuale di un file JSON con i titoli Nintendo. Nessuna verifica dell’account Nintendo e nessuna sincronizzazione automatica.",780));
        body.add(button("Importa titoli Nintendo (.json)",()->importNintendo()));
      }
      if(!link.isEmpty()){
        body.add(muted((yes(link,"verified")?"Cronologia verificata tramite il servizio":"Dati inseriti manualmente")+" · "+list(link.get("games")).size()+" titoli"));
        if(yes(link,"partial"))body.add(muted("Importazione parziale: raggiunto il limite di titoli per sincronizzazione."));
        if(!platform.equals("Nintendo")&&!yes(link,"connected"))body.add(muted("Cronologia salvata; ricollega l’account per aggiornarla."));
        JPanel actions=row(button("Mostra titoli importati",()->history(link)));
        if(yes(link,"connected"))actions.add(button("Sincronizza",()->operation("console-sync",obj("platform",platform))));
        actions.add(button("Scollega / elimina cronologia",()->{
          if(JOptionPane.showConfirmDialog(app.frame,"Eliminare la cronologia importata di "+platform+"? Le edizioni aggiunte alla libreria rimangono.",platform,JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION)operation("console-unlink",obj("platform",platform));
        }));body.add(actions);
      }
      box.add(body);result.add(box);
    }
    return result;
  }
  void operation(String route,Map<String,Object> data){
    app.work("Aggiornamento console…",()->{Map<String,Object> result=app.api.call(route,data);app.state=app.api.call("state",obj());return result;},r->{app.settings();if(r.containsKey("count"))JOptionPane.showMessageDialog(app.frame,num(r,"count")+" titoli importati. Usa Mostra titoli importati per aprirli; aggiungili alla libreria solo se li possiedi.");});
  }
  void open(String url){try{Desktop.getDesktop().browse(URI.create(url));}catch(Exception e){app.error(e);}}
  void psn(){
    JPasswordField code=passwordField(30);
    JButton login=button("1. Apri accesso PlayStation",()->open("https://www.playstation.com/"));
    JButton token=button("2. Apri codice di collegamento",()->open("https://ca.account.sony.com/api/v1/ssocookie"));
    JPanel form=col(App.wrap("Integrazione non ufficiale. Accedi sul sito PlayStation nel browser, poi apri la pagina del codice nello stesso browser. Incolla qui soltanto il valore NPSSO di 64 caratteri. Non inserire la password PlayStation. Il codice autorizza l’accesso all’account: non condividerlo in chat.",520),login,token,label("Codice NPSSO",14,true),code);
    if(JOptionPane.showConfirmDialog(app.frame,form,"Collega PlayStation · sperimentale",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)==JOptionPane.OK_OPTION){char[] value=code.getPassword();String secret=new String(value);Arrays.fill(value,'\0');code.setText("");operation("console-psn",obj("platform","PlayStation","npsso",secret));}
  }
  void startXbox(){app.work("Preparazione accesso Microsoft…",()->app.api.call("console-start",obj("platform","Xbox")),this::xboxDialog);}
  void xboxDialog(Map<String,Object> pending){
    JTextField code=field(str(pending,"code"));code.setEditable(false);
    JPanel form=col(App.wrap("Apri Microsoft nel browser e autorizza l’app usando questo codice. Poi premi Ho autorizzato.",450),code,button("Apri Microsoft",()->open(str(pending,"url"))));
    int chosen=JOptionPane.showOptionDialog(app.frame,form,"Collega Xbox",JOptionPane.DEFAULT_OPTION,JOptionPane.PLAIN_MESSAGE,null,new String[]{"Ho autorizzato","Annulla"},"Ho autorizzato");
    if(chosen!=0){app.work("Annullamento…",()->app.api.call("console-cancel",obj("platform","Xbox")),r->{});return;}
    app.work("Verifica autorizzazione Xbox…",()->{Map<String,Object> result=app.api.call("console-poll",obj("platform","Xbox"));if(!yes(result,"pending"))app.state=app.api.call("state",obj());return result;},r->{
      if(yes(r,"pending")){JOptionPane.showMessageDialog(app.frame,"Autorizzazione ancora in attesa. Attendi almeno "+num(r,"interval")+" secondi e completa l’accesso nel browser.");xboxDialog(pending);}
      else{app.settings();JOptionPane.showMessageDialog(app.frame,num(r,"count")+" titoli Xbox importati.");}
    });
  }
  void importNintendo(){
    JFileChooser chooser=new JFileChooser();chooser.setDialogTitle("Importa titoli Nintendo · esempio in docs/nintendo-import-esempio.json");
    if(chooser.showOpenDialog(app.frame)!=JFileChooser.APPROVE_OPTION)return;
    Path path=chooser.getSelectedFile().toPath();
    app.work("Importazione Nintendo…",()->{
      if(Files.size(path)>200000)throw new IllegalArgumentException("File troppo grande (massimo 200 KB).");
      Map<String,Object> file=map(Json.read(Files.readString(path)));Map<String,Object> result=app.api.call("console-import",obj("platform","Nintendo","games",file.get("games")));app.state=app.api.call("state",obj());return result;
    },r->{app.settings();JOptionPane.showMessageDialog(app.frame,num(r,"count")+" titoli importati manualmente.");});
  }
  void history(Map<String,Object> link){
    app.shell("Impostazioni");JPanel body=col(label(str(link,"platform")+" · titoli importati",26,true),button("Torna ai collegamenti",()->app.settings()),App.wrap("La presenza nella cronologia non indica automaticamente possesso o completamento. Apri un titolo per scegliere lo stato della tua edizione.",1000));
    for(Object item:list(link.get("games"))){Map<String,Object> game=map(item);JPanel row=card();row.add(label(str(game,"name"),18,true),BorderLayout.NORTH);row.add(muted(str(game,"platforms")+(game.containsKey("earned")?" · Trofei / obiettivi "+num(game,"earned")+" / "+num(game,"total"):" · Importato manualmente")));row.add(button("Apri",()->app.detail(app.game(str(game,"id")))),BorderLayout.EAST);body.add(row);}
    app.showBody(body);
  }
}
