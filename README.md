# Game Backlog Manager · 2.6 beta

App desktop Java 17 con backend account e social.

## Pubblicazione gratuita

Il progetto è predisposto per Render Free + Neon Free. Leggi [HOSTING-GRATUITO.md](HOSTING-GRATUITO.md). Il backend è pubblicato su Render e usa Neon PostgreSQL.

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

## Novità 2.4

Edizioni possedute e stati per piattaforma, marker nel catalogo e nel selettore delle edizioni, filtri del profilo sulle piattaforme effettivamente possedute. Il voto e i preferiti restano globali al gioco. I dati precedenti multipiattaforma vengono conservati come Edizione non specificata, senza attribuzioni automatiche.

Dalla registrazione puoi scegliere Prova senza account: apre una demo locale con dati temporanei, isolata dal server remoto. Modalità scura verde/oro in Impostazioni, disattivata inizialmente, e comando Esci nella barra superiore.

## Correzioni 2.5

Pulsanti misurati e disegnati con lo stesso metodo in tutte le pagine, per evitare il troncamento introdotto nella 2.4. Simbolo Statistiche ridisegnato interamente. Scheda gioco ricostruita dalla prima bozza approvata: banner con copertina sovrapposta, comandi laterali, dieci cerchi per il voto, recensioni e attività affiancate. Tema scuro aggiornato come anteprima smeraldo/oro con bordi sottili, selezionabile in Impostazioni.

Verifica automatica con Metal e Nimbus e diverse scale; schermate del programma su Linux. Da confermare sul computer Windows dell'utente.

## Anteprima 2.6

Logo cliccabile per tornare al menu da ogni pagina; avatar collegato al profilo, campi facoltativi età/sesso/provenienza/bio, preferiti centrati, anteprima della libreria, liste e amici. La nuova disposizione del profilo è in attesa di approvazione. Le sezioni riservate rispettano la privacy della libreria e le liste private restano visibili solo al proprietario. I campi biografici inseriti volontariamente sono pubblici.

La copertina piccola si sovrappone nuovamente al banner, come nella bozza approvata e su richiesta dell’utente. Il cambio di edizione aggiorna i componenti senza ricostruire la pagina né interrompere la recensione; il voto usa solo i dieci cerchi e una lettura numerica, senza campo o frecce. Icona Statistiche ridotta, nessun placeholder nel campo email, messaggi dei grafici vuoti allineati in alto.
