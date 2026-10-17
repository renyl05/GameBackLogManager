# Console · prima implementazione 2.8

## Capacità

PlayStation: flusso NPSSO → codice → token, endpoint trophy/v1/users/me/trophyTitles, paginazione e riepilogo earned/defined. È un adattatore sperimentale non ufficiale. Il codice va inserito soltanto nella propria app, mai in chat o nel repository. Nessun refresh token è conservato.

Xbox: device-code Microsoft per account personali, scope XboxLive.signin, scambio user token/XSTS e achievements.xboxlive.com history/titles. Registrare una propria app e abilitare il flusso appropriato; configurare XBOX_CLIENT_ID su Render. Il client ID non è un segreto. L’autorizzazione ai servizi Xbox deve essere verificata per l’app registrata. L’interfaccia disabilita il collegamento quando manca la configurazione. La cronologia obiettivi non è la lista completa dei giochi posseduti.

Nintendo: importazione manuale validata, massimo 200 titoli/file, deduplicazione e sostituzione atomica della precedente importazione. Non viene dichiarato un accesso API ufficiale non disponibile nella configurazione attuale. Non si usano certificati console estratti o servizi di attestation di terzi.

## Dati e sicurezza

consoleLinks è separata da links (Steam); stato e progressi visibili soltanto al proprietario. I token vivono solo nella memoria del processo. Lo stato restituito al client e l’export contengono esclusivamente dati importati, mai token. I giochi entrano nel catalogo senza il campo earned, senza associazione pubblica all’utente e senza creare entries. I cataloghi possono contenere metadati comuni di titoli importati; lo scollegamento non li cancella.

Le chiamate console di rete avvengono fuori dal lock globale del server, con autenticazione verificata nuovamente prima della scrittura. Limiti: rate utente, scadenza device code, intervallo poll Microsoft, timeout e limite pagine. partial segnala il raggiungimento del limite di paginazione. Un’interruzione del backend richiede una nuova autenticazione.

## Fonti consultate

- psn-api, documentazione primaria del progetto non ufficiale: https://psn-api.achievements.app/authentication/authenticating-manually
- Codice autorizzazione: https://github.com/achievements-app/psn-api/blob/main/src/authenticate/exchangeNpssoForAccessCode.ts
- Scambio token: https://github.com/achievements-app/psn-api/blob/main/src/authenticate/exchangeAccessCodeForAuthTokens.ts
- Cronologia PSN: https://github.com/achievements-app/psn-api/blob/main/src/trophy/user/getUserTitles.ts
- Microsoft device code: https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-device-code
- Xbox title history: https://learn.microsoft.com/en-us/gaming/gdk/docs/reference/live/rest/uri/titlehistory/uri-titlehistoryusersxuidhistorytitlesgetv2
- Nintendo Developer Portal FAQ: https://developer.nintendo.com/faq

## Verifica

ConsoleTest usa un server locale e risposte simulate; nessun account console reale o token personale è incluso. Verifica: PSN, OAuth Xbox, paginazione PSN, authorization_pending, privacy tra account, token assenti da snapshot/export, import Nintendo idempotente, invalidazione dopo logout, rimozione selettiva dei collegamenti e metadati IGDB con ID invariato. Gli esiti simulati non attestano il funzionamento live dei servizi esterni.
