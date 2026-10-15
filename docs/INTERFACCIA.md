# Correzioni grafiche · 2.3 beta

Il menu iniziale ora è composto da pulsanti reali, con pannelli e testi disegnati nativamente. Non viene più ingrandita l’intera immagine del menu. Le icone originali sono conservate e visualizzate senza superare la risoluzione disponibile, considerando anche la densità dello schermo. Non si promette di ricostruire dettagli assenti nelle immagini originali.

Il logo usa nuovamente i pixel originali del riferimento, con sfondo trasparente e ridimensionamento nearest-neighbor per conservarne l’aspetto. Non viene sostituito da contorni smussati o da un altro font e non contiene joystick aggiunti. Il resto dell’interfaccia usa Fredoka incorporato, con pesi statici derivati dalla famiglia ufficiale Google Fonts; la licenza OFL è inclusa.

Campi email e password, menu a tendina, checkbox, spinner del voto, pulsanti, cuori e barre di scorrimento condividono font, colori e geometria. I cuori sono icone vettoriali, non caratteri mancanti nel font. I bordi dei riquadri usano la stessa sagoma dello sfondo; il campo recensione è ritagliato correttamente agli angoli.

Le copertine IGDB richiedono la variante 2x; le immagini panoramiche usano la variante 1080p. Steam usa library_hero quando disponibile e ripiega su header. Il rendering applica interpolazione bicubica e maschere arrotondate alla densità effettiva del display. Il ritaglio mantiene le proporzioni. Le immagini non disponibili sono segnaposto dichiarati: senza credenziali IGDB non compaiono copertine reali.

## Verifica

Schermate renderizzate a 1420×900 e 1100×760, più verifica del rendering al 200%. Test aggiuntivi: font condiviso, caricamento del logo, pulsanti del menu e aree cliccabili, assenza di sovrapposizioni, azione Esci, risoluzione del buffer immagini e trasparenza degli angoli. I test precedenti di account, social, privacy, provider e SQL restano superati.

Le immagini in questa cartella mostrano il programma reale con dati di test locali; non sono concept generati. La verifica è stata eseguita su Linux con Java 17, non su un desktop Windows reale.

![Menu](ui/menu.png)

![Catalogo senza IGDB configurato](ui/catalogo.png)

## Correzioni 2.3

I pannelli verticali applicano spaziatura e allineamento anche ai componenti aggiunti successivamente. I campi dei moduli e la checkbox condividono lo stesso margine sinistro. I pulsanti mantengono la larghezza del contenuto; l’avatar ha dimensioni quadrate e forma circolare. Le icone Statistiche ed Esci sono ricavate da ritagli completi. Home non disegna più un fondo bianco sotto il bordo azzurro.

La pagina gioco separa banner, intestazione e riquadri recensione. Il selettore dell’edizione varia la sagoma della copertina in base ai formati fisici più comuni della piattaforma (es. Switch, DS, custodie DVD e jewel case). Le proporzioni sono valori predefiniti: edizioni e regioni possono avere custodie differenti. L’immagine originale viene adattata senza deformarla o ritagliarla.
