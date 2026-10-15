# Pubblicazione gratuita: Render + Neon

Questa versione **2.1 beta** include l'adattamento per ospitare il server Java su Render Free, conservando i dati su Neon Free. Non è ancora pubblicata: servono i tuoi account. La grafica e i tuoi sorgenti originali sono conservati.

## Prima di cominciare

- **Render Free** esegue il server, ma sospende il servizio dopo 15 minuti di inattività. La prima richiesta successiva può attendere circa un minuto. Il disco locale è temporaneo.
- **Neon Free** conserva il database PostgreSQL separatamente, entro i limiti gratuiti. Il piano gratuito non ha una scadenza temporale dichiarata; le quote e le condizioni possono cambiare.
- Nessun disco a pagamento, nessun database Render con scadenza di 30 giorni, nessun dominio personalizzato necessario. Si usa l'indirizzo HTTPS `onrender.com` assegnato al servizio.
- Mantieni entrambi i servizi sui piani **Free**. Se l'interfaccia propone un piano a pagamento, fermati e verifica la selezione. Questo pacchetto non attiva né acquista servizi.

Documentazione verificata il 16 ottobre 2026:
https://render.com/docs/free
https://neon.com/pricing
https://render.com/docs/docker
https://render.com/docs/blueprint-spec

## 1 · Database Neon

1. Crea un account su https://neon.com/ e un progetto sul piano **Free**.
2. Scegli una regione europea vicina al server (per esempio Francoforte, se disponibile).
3. Apri i dettagli di connessione e annota **host, nome database, utente e password**. Sono segreti: non pubblicarli su GitHub e non incollarli in chat.
4. Prepara queste variabili, che andrai a inserire su Render:

| Variabile | Valore |
|---|---|
| `JDBC_DATABASE_URL` | `jdbc:postgresql://HOST_NEON/NOME_DATABASE` |
| `JDBC_DATABASE_USER` | Utente del database Neon |
| `JDBC_DATABASE_PASSWORD` | Password del database Neon |

L'URL JDBC deve avere **solo host e database**, senza username/password incorporati e senza `?sslmode=...` o altri parametri. Il codice imposta già TLS con verifica del certificato e del nome host. Non incollare direttamente la stringa `postgresql://utente:password@...` nel campo JDBC.

Il server crea da solo la tabella `gbm_state`. Non devi importare schema SQL manualmente. Non impostare una variabile JDBC vuota per far partire il server: in configurazione cloud la mancanza del database blocca l'avvio, proprio per evitare perdite di dati.

## 2 · Repository GitHub

Crea un repository, preferibilmente **privato**, e carica il contenuto di questa cartella nella sua radice. Nella radice devono esserci `Dockerfile`, `render.yaml`, `src`, `resources` e `lib/postgresql.jar`.

**Non caricare** `server.properties` dopo aver inserito credenziali, file `.env`, cartella `data` o esportazioni con dati personali. Il `.gitignore` incluso li esclude se usi Git; il caricamento manuale via browser può richiedere di deselezionarli esplicitamente. Il Dockerfile non copia questi file nell'immagine.

## 3 · Servizio Render

1. Crea un account su https://render.com/ e collega il repository.
2. Crea un **Blueprint** dal repository: il file `render.yaml` definisce un solo Web Service Docker sul piano **Free**.
3. Inserisci i tre valori `JDBC_DATABASE_*` quando richiesti. Non aggiungere un database Render o un disco persistente a pagamento.
4. Avvia la pubblicazione. Il Dockerfile compila i sorgenti con Java 17 e avvia **soltanto il server**, non l'interfaccia Swing.
5. Render imposta `PORT` e `RENDER_EXTERNAL_URL`; il server li utilizza. Il binding è `0.0.0.0` e il controllo salute è `GET /health`.
6. Attendi che il servizio sia pronto e apri `https://NOME-SERVIZIO.onrender.com/health`: deve rispondere con `ok: true` e `storage: postgresql`.

Se preferisci creare il servizio senza Blueprint: runtime Docker, piano Free, regione Francoforte, percorso Dockerfile `./Dockerfile`, health check `/health`, variabili `BIND_HOST=0.0.0.0`, `REQUIRE_DATABASE=true` e le tre credenziali JDBC. Non sovrascrivere `PORT` se non necessario.

Il programma usa un'unica istanza server per database. Non avviare due servizi distinti sullo stesso database: la revisione dello snapshot impedisce che uno sovrascriva silenziosamente l'altro.

## 4 · Collegare l'app Windows

Apri **AVVIA-CLIENT-REMOTO.bat** e inserisci il tuo URL, per esempio:

`https://NOME-SERVIZIO.onrender.com`

Ogni giocatore deve usare lo stesso URL. Gli account creati localmente appartengono al database locale e non vengono trasferiti automaticamente: sulla nuova installazione remota si registrano nuovi account. Per una migrazione completa, conserva il file locale originale e pianifica il trasferimento prima di cominciare a usare entrambi i database.

Il servizio gratuito può perdere le sessioni quando viene sospeso/riavviato: l'app torna al login senza eliminare la libreria salvata su Neon. Nessuna password viene memorizzata nel client per accedere automaticamente. Dopo inattività, attendi il riavvio e riprova se la prima richiesta fallisce.

## 5 · Catalogo e Steam

Nelle Environment Variables di Render aggiungi, quando disponibili:

- `IGDB_CLIENT_ID`, `IGDB_CLIENT_SECRET` per catalogo esteso e immagini.
- `STEAM_API_KEY` per ore e obiettivi.

Non includere questi segreti nel repository. Per Steam, in assenza di un `PUBLIC_BASE_URL` personalizzato il server usa l'URL HTTPS assegnato da Render. Non serve acquistare un dominio.

## 6 · Recupero password via email (facoltativo)

Render Free blocca le porte SMTP comuni: la configurazione SMTP della versione locale non è sufficiente.

È incluso un adattatore **Brevo via HTTPS**. Se vuoi attivarlo, crea un account, abilita l'invio transazionale e verifica un mittente seguendo le istruzioni del servizio; sul server imposta:

- `BREVO_API_KEY`: chiave API del tuo account.
- `SMTP_FROM`: email del mittente autorizzato su Brevo.

L'invio usa HTTPS sulla porta 443. Il piano gratuito di Brevo dichiara 300 invii giornalieri; l'effettiva abilitazione e i requisiti del mittente vanno verificati nell'account. Il pacchetto non crea l'account e non invia email di prova. Registrazione e accesso funzionano anche senza questa configurazione; il recupero password mostra un errore esplicito finché non c'è un servizio configurato.

Fonti:
https://render.com/docs/free
https://developers.brevo.com/docs/send-a-transactional-email
https://help.brevo.com/hc/en-us/articles/208589409-About-Brevo-s-pricing-plans

## Verifica dopo la pubblicazione

1. Crea un account di prova dall'app collegata al server remoto.
2. Aggiungi un preferito e un gioco in libreria.
3. Riavvia il servizio Render, attendi che ritorni disponibile e accedi nuovamente.
4. Verifica che preferito e gioco siano ancora presenti: questa è la verifica reale della persistenza su Neon.
5. Prova un secondo account per amicizie e controlli di privacy.
6. Configura e prova separatamente IGDB, Steam e l'invio delle email.

**Non è stato effettuato un deploy reale**, né un test con il tuo database Neon. In questo ambiente sono verificati compilazione, test del backend e logica SQL su H2 in modalità PostgreSQL. L'immagine Docker è preparata ma non è stata costruita qui, perché Docker non è disponibile. I limiti della beta restano elencati in `LEGGIMI.md`.
