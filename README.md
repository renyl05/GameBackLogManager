# Game Backlog Manager

App desktop Java 17 con backend account e social.

## Pubblicazione gratuita

Il progetto è predisposto per Render Free + Neon Free. Leggi [HOSTING-GRATUITO.md](HOSTING-GRATUITO.md). Il backend non è ancora pubblicato.

## Compilazione

Windows: `COMPILA.bat`. Linux/macOS: `./build.sh`. Avvio: `java -jar dist/GameBackLogManager.jar`.

Per il servizio cloud usare il Dockerfile, che avvia `gbm.Server` senza GUI. Nessun segreto o database personale deve essere inserito nel repository.

## Stato

Catalogo esteso IGDB, Steam e posta richiedono configurazione. PlayStation/Xbox/Nintendo non sono integrati. I test dei provider usano risposte simulate; i test SQL usano H2 in modalità PostgreSQL, senza connessione a un database Neon reale.
