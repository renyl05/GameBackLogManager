# Attivazione dei servizi esterni

Le chiavi vanno nelle variabili Environment del servizio Render, mai nel repository o nel client desktop.

| Funzione | Variabili richieste | Provenienza |
| --- | --- | --- |
| Catalogo e immagini | `IGDB_CLIENT_ID`, `IGDB_CLIENT_SECRET` | Applicazione Confidential nella console sviluppatori Twitch |
| Ore e obiettivi Steam | `STEAM_API_KEY` | Steam Community Web API |
| Recupero password via HTTPS | `BREVO_API_KEY`, `SMTP_FROM` | Chiave API v3 Brevo e indirizzo mittente verificato |

Render: https://dashboard.render.com/web/srv-db8m2o6q1p3s73bkcam0

IGDB: https://api-docs.igdb.com/ — richiede un account Twitch con 2FA e un’app registrata su https://dev.twitch.tv/console/apps . Per il flusso client credentials IGDB non usa il redirect OAuth.

Steam: https://steamcommunity.com/dev/apikey — usa `gamebacklogmanager.onrender.com` come dominio. Poi il giocatore collega il proprio profilo Steam dalle impostazioni dell’app; i dettagli dei giochi devono essere accessibili alla Web API. Non si inserisce la password Steam nell’app.

Brevo: https://help.brevo.com/hc/en-us/articles/209467485-Create-and-manage-your-API-keys — genera una chiave API v3 (non una chiave SMTP o MCP); configura e verifica il mittente. `SMTP_FROM` deve coincidere con il mittente autorizzato.

Dopo Save/Rebuild su Render controllare `/api/health`: i flag indicano la presenza delle variabili, non la validità delle chiavi. Verificare poi una ricerca nel catalogo, un’importazione da un proprio account Steam e il recupero della password del proprio account di test. Le prove di invio email devono essere autorizzate dal titolare dell’indirizzo.

Al rilascio 2.3 nessuna chiave IGDB/Steam/Brevo è stata fornita o configurata. I provider sono verificati con fixture; l’attivazione reale resta in attesa dei relativi account.

## Attivare IGDB quando il browser cloud è rifiutato

Completa questi passaggi nel tuo browser personale. Non è sufficiente accedere lì e poi tornare al browser cloud: le sessioni non sono condivise.

1. Apri https://dev.twitch.tv/console/apps e accedi al tuo account Twitch. Per registrare applicazioni è richiesta la verifica in due passaggi su Twitch.
2. Registra un’applicazione con un nome univoco, per esempio GameBackLogManager-renyl05. Imposta Client Type su Confidential. IGDB non usa il redirect: come valore richiesto dal form puoi usare http://localhost.
3. Apri Manage e genera un Client Secret con New Secret. Usa il Client ID e il segreto della stessa applicazione.
4. Apri il servizio GameBackLogManager nel Dashboard Render, poi Environment. Aggiungi IGDB_CLIENT_ID con il Client ID e IGDB_CLIENT_SECRET con il Client Secret.
5. Salva le variabili e distribuisci nuovamente il servizio. Non sostituire o cancellare le altre variabili. Le credenziali restano sul backend: non inserirle nel client, nel repository o nella chat.

Dopo la distribuzione si può verificare la configurazione tramite /api/health. Il flag igdb indica che entrambe le variabili sono presenti, non che le credenziali siano valide: per verificare la connessione effettiva va eseguita anche una ricerca nel catalogo.

Riferimento ufficiale: https://api-docs.igdb.com/#account-creation
