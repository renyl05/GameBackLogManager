# Verifica della build

- Compilazione con OpenJDK 17, sorgenti UTF-8, target Java 17: riuscita.
- 32 controlli di integrazione su backend reale avviato durante i test: riusciti.
- 14 controlli aggiuntivi sui provider con risposte HTTP simulate: riusciti.
- Apertura e rendering delle schermate su display virtuale Linux a 1420×900; controllo aggiuntivo Impostazioni a 1100×760. Corretti proporzioni del logo, altezza delle copertine e contenimento orizzontale.
- 7 controlli SQL aggiuntivi su H2 in modalità PostgreSQL: persistenza, conflitti di revisione, nessun ripiego locale, configurazione cloud, protezione TLS e health probe GET.
- JAR client senza dipendenze esterne; driver PostgreSQL incluso separatamente per il backend cloud. I test non sono inclusi nel JAR applicativo.
- Nessun account dimostrativo viene installato. I test usano directory temporanee.

## Limiti della verifica

Non è stato eseguito Windows in questo ambiente: gli script batch richiedono una prova sul PC destinatario. Nessuna verifica dal vivo di IGDB, Steam OpenID/Web API o SMTP è stata effettuata, perché non sono state fornite credenziali. I test con risposte simulate verificano costruzione delle richieste e trasformazione dei dati; non dimostrano che un account reale esponga i dati richiesti. PlayStation/Xbox/Nintendo non sono implementati.

Aggiornamento 2.1: adattatore Brevo verificato con risposta HTTP simulata. Dockerfile e Render Blueprint preparati, ma non costruiti/pubblicati in questo ambiente. Nessuna connessione live Neon effettuata.
