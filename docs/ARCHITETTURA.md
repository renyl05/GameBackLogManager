# Struttura

| File | Responsabilità |
|---|---|
| `App.java` | Finestre Swing, navigazione, form, grafici e operazioni asincrone |
| `Theme.java` | Palette, riquadri, campi, immagini asincrone e logo originale |
| `Api.java` | Client HTTP JSON; HTTPS obbligatorio per server remoti |
| `Server.java` | Account, autorizzazioni, social, librerie e collegamento Steam OpenID |
| `Store.java` | Snapshot JSON atomici e catalogo iniziale |
| `SnapshotDatabase.java` | Snapshot persistenti PostgreSQL e controllo delle revisioni |
| `Providers.java` | IGDB/Twitch e Steam Web API |
| `Mailer.java` | Codici di recupero via SMTP STARTTLS o HTTPS Brevo |
| `Json.java` | Codifica/decodifica JSON, senza deserializzazione di oggetti Java |

Il package originale `gamebacklogmanager` è conservato separatamente. Il nuovo package `gbm` introduce account multipli e servizio remoto; non esegue il vecchio menu Scanner dentro gli eventi Swing.

## Identità dei dati

Ogni gioco ha un ID con prefisso del fornitore (`local:`, `igdb:`, `steam:`). Ogni voce personale associa utente e gioco e può avere `favorite=true` senza uno stato di libreria. Gli stati FINITO / IN_CORSO determinano il possesso secondo il progetto originale; DA_GIOCARE e DESIDERATO non sono conteggiati come posseduti. I voti non assegnati sono null.

Le statistiche sulle piattaforme del catalogo descrivono la disponibilità dei titoli, non dimostrano che l'utente possieda tutte le edizioni. Le ore sono associate alla specifica versione Steam importata. Questa beta non unifica automaticamente edizioni differenti.

## Persistenza e concorrenza

Le mutazioni del backend sono serializzate. Lo snapshot viene scritto in un file temporaneo e rinominato sullo stesso volume. Su filesystem POSIX viene impostata la modalità 0600. Sul computer Windows applicare i permessi della cartella dati secondo gli utenti della macchina. Non avviare due server sullo stesso database.

Lo snapshot JSON è adatto alle prove e a piccoli gruppi, non è un database transazionale per molte istanze. Le sessioni e i codici di recupero vivono in memoria. Le chiavi esterne rimangono sul server.

## Privacy

L'accesso alla libreria è verificato sul server: proprietario, pubblico, oppure amicizia accettata e visibilità Solo amici. Statistiche, trofei e attività hanno controlli separati. Condividere solo i trofei non rivela i minuti né lo SteamID; condividere solo le statistiche non include la cache dei trofei.

Recensioni e liste esplicitamente pubbliche sono contenuti condivisi separatamente dalla libreria. Nessun token o hash password è incluso nei profili o nell'esportazione.

## Possibili sviluppi successivi

Connettori con accesso autorizzato ai servizi console, associazioni esplicite tra ID dei provider, database relazionale per più istanze, verifica della casella email, traduzioni e distribuzione con runtime Windows incluso.

## Hosting gratuito

Il Dockerfile avvia soltanto il backend su Render. Il database Neon conserva uno snapshot in una riga con revisione ottimistica; non è ancora una normalizzazione relazionale delle entità. In modalità cloud il database è obbligatorio e non si ripiega sul filesystem temporaneo. TLS usa il trust store Java e verifica il nome host.
