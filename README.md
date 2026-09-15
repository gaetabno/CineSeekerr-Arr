# CineSeekerr Arr

Bot Telegram self-hosted per scegliere manualmente una release tramite **Radarr** e **Sonarr**.

## Architettura

`Telegram → CineSeekerr → Radarr/Sonarr → Transmission → import/rinomina/libreria`

Il bot usa TMDB solo per la disambiguazione. Quando scegli un titolo, lo aggiunge a Radarr o Sonarr con la ricerca automatica disabilitata; poi esegue una ricerca **manuale** nell'API Arr, mostra solo release approvate da Arr e invia la release completa scelta. Radarr/Sonarr possiedono download, importazione, rinomina e organizzazione della libreria.

Il bot accede direttamente a Transmission esclusivamente per due comandi amministrativi con conferma obbligatoria:

- `/clear`: rimuove tutti i torrent completati/in seed mantenendo i dati scaricati;
- `/elimina`: mostra le risorse eliminabili a pagine, consente di selezionarne una sola e, dopo una seconda conferma esplicita, rimuove quel torrent e i relativi dati dalla cartella download di Transmission.

Entrambi rileggono e convalidano i torrent al momento della conferma, non includono mai download incompleti e non chiamano API di eliminazione di Radarr/Sonarr. `/elimina` accetta soltanto percorsi contenuti in `TRANSMISSION_ALLOWED_DOWNLOAD_DIRS` (default `/downloads/complete`), interpretati nello spazio dei percorsi del container Transmission. Più directory possono essere fornite come lista separata da virgole. Il controllo viene ripetuto immediatamente prima della RPC di rimozione; Transmission non offre una verifica e cancellazione atomica del percorso, quindi l'operatore deve impedire spostamenti concorrenti dei torrent durante la conferma. Il container del bot continua a non avere mount delle cartelle download o media.

Per TV restano distinti stagione completa ed episodio singolo. Sonarr risolve la serie tramite lookup TMDB/TVDB; non viene mai abbinata solo per titolo.

## Requisiti

- Docker e una rete Docker già condivisa da Radarr, Sonarr e Transmission.
- Radarr/Sonarr configurati con indexer (anche sincronizzati da Prowlarr), quality profile, root folder, download client e Completed Download Handling/import/rename.
- Bot Telegram, chat ID autorizzato, chiave TMDB e credenziali RPC Transmission.

## Avvio

```bash
cp .env.example .env
# modifica .env: rete, token, chiavi Arr, profili e root folder esistenti in Arr
docker compose --env-file .env config
docker compose --env-file .env up -d --build
```

Non montare cartelle download o media nel bot. `RADARR_ROOT_FOLDER` e `SONARR_ROOT_FOLDER` sono i percorsi già configurati e visibili ai rispettivi Arr.

## Configurazione

Variabili obbligatorie: `TELEGRAM_BOT_TOKEN`, `TELEGRAM_ALLOWED_CHAT_IDS`, `TMDB_API_KEY`, `ARR_NETWORK`, `RADARR_API_KEY`, `RADARR_QUALITY_PROFILE_ID`, `RADARR_ROOT_FOLDER`, `SONARR_API_KEY`, `SONARR_QUALITY_PROFILE_ID`, `SONARR_ROOT_FOLDER`, `TRANSMISSION_USERNAME`, `TRANSMISSION_PASSWORD`.

`TRANSMISSION_ALLOWED_DOWNLOAD_DIRS` limita le directory dalle quali `/elimina` può cancellare dati; il valore predefinito è `/downloads/complete`. I percorsi devono essere assoluti e riferiti al filesystem visto dal container Transmission.

`RADARR_URL`, `SONARR_URL` e `TRANSMISSION_RPC_URL` hanno valori interni Docker predefiniti. `BOT_LANGUAGE=it` è il default; è disponibile anche `en`.

La cache delle release manuali Arr è temporanea (circa 30 minuti): se un grab risponde che la release non è più valida, ripeti la ricerca e seleziona di nuovo.

## Sviluppo

```bash
docker run --rm -v "$PWD":/workspace -w /workspace maven:3.9-eclipse-temurin-21 ./mvnw test
docker run --rm -v "$PWD":/workspace -w /workspace maven:3.9-eclipse-temurin-21 ./mvnw package
docker build -t cineseekerr-arr:local .
```

Le API usate sono le API v3 pubbliche di Radarr/Sonarr: lookup, creazione senza auto-search, manual search `POST /release`, elenco cache `GET /release` e grab `POST /release` con `guid`/`indexerId`.
