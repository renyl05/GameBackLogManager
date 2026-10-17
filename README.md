# Game Backlog Manager · 2.8 beta

App desktop Java 17 con backend account e social.

## Pubblicazione gratuita

Il progetto è predisposto per Render Free + Neon Free. Leggi [HOSTING-GRATUITO.md](HOSTING-GRATUITO.md). Il backend è pubblicato su Render e usa Neon PostgreSQL.

## Compilazione

AVVIA.bat usa il server online condiviso per impostazione predefinita. AVVIA-LOCALE.bat avvia il server locale, con account e libreria separati.

Il repository include già `dist/GameBackLogManager.jar`: su Windows puoi estrarre lo ZIP e aprire `AVVIA.bat` con Java 17 o superiore.

Per ricompilare su Windows: `COMPILA.bat`. Linux/macOS: `./build.sh`. Avvio: `java -jar dist/GameBackLogManager.jar`.

Per il servizio cloud usare il Dockerfile, che avvia `gbm.Server` senza GUI. Nessun segreto o database personale deve essere inserito nel repository.

## Stato

Catalogo esteso IGDB, Steam e posta richiedono configurazione. Le prime integrazioni console sono descritte nella sezione 2.8, con limiti e configurazione distinti. I test dei provider usano risposte simulate; i test SQL usano H2 in modalità PostgreSQL, con un controllo separato del backend pubblicato su Render e dello storage Neon.

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

## Interfaccia 2.7

Catalogo più compatto: fino a otto giochi per riga alla dimensione standard, con testo dei comandi invariato. Sfondo della scheda esteso all’intera sezione prima di recensioni e attività amici; due pannelli semitrasparenti per informazioni e comandi. Copertina fissa 174 × 250 pixel per tutte le edizioni, immagine intera senza deformazioni. Il cambio di edizione conserva la recensione in scrittura. Menu aggiornato con console intera e trasparenze che conservano le superfici chiare.

Per usare il server già configurato, avvia AVVIA-CLIENT-REMOTO.bat e inserisci https://gamebacklogmanager.onrender.com. Per queste modifiche al client non occorre cambiare variabili di ambiente del server.

Verificato su Java 17/Linux, con temi chiaro e scuro, finestre 1420 × 900 e 1100 × 760, testi lunghi e scala testo 100%/115%. Test funzionali locali e provider simulati passati. Anteprime con artwork reale Steam e account locale temporaneo: non costituiscono una nuova verifica delle credenziali IGDB del server remoto. Da verificare sul proprio Windows.

## Versione 2.8 · catalogo online e prime integrazioni console

L’avvio normale usa https://gamebacklogmanager.onrender.com; la schermata di accesso mostra il server. Gli account locali non vengono trasferiti automaticamente al server online. Le configurazioni esplicite GBM_SERVER_URL e -Dgbm.server restano disponibili.

Il catalogo aggiorna anche i metadati già memorizzati. Quando si apre un titolo importato senza copertina, l’app cerca una corrispondenza esatta su IGDB per aggiungere immagini e descrizione, mantenendo l’ID e le edizioni già salvate. Nessun abbinamento approssimativo automatico.

In Impostazioni → Account collegati → Console:
- PlayStation: collegamento sperimentale non ufficiale tramite NPSSO inserito nell’app; cronologia titoli con conteggio dei trofei. Non viene chiesta la password Sony.
- Xbox: accesso con codice nel browser Microsoft, poi cronologia dei titoli con progressi negli obiettivi. Richiede una propria app Microsoft e XBOX_CLIENT_ID configurato sul server; l’app può richiedere ulteriori autorizzazioni ai servizi Xbox.
- Nintendo: importazione manuale JSON, non collegamento autenticato. Esempio in docs/nintendo-import-esempio.json. Un nuovo file sostituisce la precedente cronologia Nintendo importata.

Le cronologie restano private; non aggiungono automaticamente giochi posseduti o completati. Lo scollegamento elimina la cronologia e la sessione della sola console scelta, mantenendo le edizioni già aggiunte alla libreria. I metadati dei titoli restano nel catalogo comune, senza progressi personali.

NPSSO, access token e credenziali temporanee di collegamento non sono salvati in file, esportazioni o database. I collegamenti richiedono una nuova autorizzazione dopo scadenza, logout o riavvio del backend. I conteggi sono iniziali: niente sincronizzazione delle ore console né elenco dettagliato dei singoli trofei in questa fase.

Test automatici con risposte simulate per OAuth, paginazione, isolamento degli account, importazioni atomiche e protezione dei token. Non ancora verificati con account reali PlayStation/Xbox. Il catalogo IGDB risulta configurato sul server remoto, ma la verifica dei propri account va fatta nell’app online.

Ogni nuova versione deve essere sincronizzata nel repository GitHub con sorgenti, risorse e JAR aggiornato; lo ZIP è una distribuzione dello stesso stato.
