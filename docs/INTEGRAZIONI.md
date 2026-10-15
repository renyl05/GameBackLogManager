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
