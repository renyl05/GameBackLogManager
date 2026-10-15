# Game Backlog Manager · 2.3 beta

App desktop Java 17 con backend account e social.

## Pubblicazione gratuita

Il progetto è predisposto per Render Free + Neon Free. Leggi [HOSTING-GRATUITO.md](HOSTING-GRATUITO.md). Il backend non è ancora pubblicato.

## Compilazione

Il repository include già `dist/GameBackLogManager.jar`: su Windows puoi estrarre lo ZIP e aprire `AVVIA.bat` con Java 17 o superiore.

Per ricompilare su Windows: `COMPILA.bat`. Linux/macOS: `./build.sh`. Avvio: `java -jar dist/GameBackLogManager.jar`.

Per il servizio cloud usare il Dockerfile, che avvia `gbm.Server` senza GUI. Nessun segreto o database personale deve essere inserito nel repository.

## Stato

Catalogo esteso IGDB, Steam e posta richiedono configurazione. PlayStation/Xbox/Nintendo non sono integrati. I test dei provider usano risposte simulate; i test SQL usano H2 in modalità PostgreSQL, con un controllo separato del backend pubblicato su Render e dello storage Neon.

## Interfaccia aggiornata

[Dettagli e schermate reali](docs/INTERFACCIA.md). Logo originale, tipografia incorporata e controlli coerenti, menu nativo e rendering HiDPI. Per configurare il servizio locale, copia `server.properties.example` in `server.properties` e modifica soltanto la copia locale.

## Server condiviso

Avvia `AVVIA-CLIENT-REMOTO.bat` e inserisci `https://gamebacklogmanager.onrender.com`. Il backend usa Neon PostgreSQL; IGDB, Steam e posta richiedono ancora le chiavi dei rispettivi account.
