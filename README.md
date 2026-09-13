# CineSeekerr Arr

Bot Telegram self-hosted per scegliere manualmente una release tramite **Radarr** e **Sonarr**.

## Architettura

`Telegram → CineSeekerr → Radarr/Sonarr → download client configurato in Arr → import/rinomina/libreria`

Il bot usa TMDB solo per la disambiguazione. Quando scegli un titolo, lo aggiunge a Radarr o Sonarr con la ricerca automatica disabilitata; poi esegue una ricerca **manuale** nell'API Arr, mostra solo release approvate da Arr, e invia la release scelta con `guid` e `indexerId`. Radarr/Sonarr possiedono download, Transmission (se configurato), importazione, rinomina e organizzazione della libreria. Il bot non ha credenziali né chiamate a Transmission, Prowlarr, qBittorrent, Plex o al filesystem media.

Per TV restano distinti stagione completa ed episodio singolo. Sonarr risolve la serie tramite lookup TMDB/TVDB; non viene mai abbinata solo per titolo.

## Requisiti

- Docker e una rete Docker già condivisa da Radarr e Sonarr.
- Radarr/Sonarr configurati con indexer (anche sincronizzati da Prowlarr), quality profile, root folder, download client e Completed Download Handling/import/rename.
- Bot Telegram, chat ID autorizzato e chiave TMDB.

## Avvio

```bash
cp .env.example .env
# modifica .env: rete, token, chiavi Arr, profili e root folder esistenti in Arr
docker compose --env-file .env config
docker compose --env-file .env up -d --build
```

Non montare cartelle media nel bot. `RADARR_ROOT_FOLDER` e `SONARR_ROOT_FOLDER` sono i percorsi già configurati e visibili ai rispettivi Arr.

## Configurazione

Variabili obbligatorie: `TELEGRAM_BOT_TOKEN`, `TELEGRAM_ALLOWED_CHAT_IDS`, `TMDB_API_KEY`, `ARR_NETWORK`, `RADARR_API_KEY`, `RADARR_QUALITY_PROFILE_ID`, `RADARR_ROOT_FOLDER`, `SONARR_API_KEY`, `SONARR_QUALITY_PROFILE_ID`, `SONARR_ROOT_FOLDER`.

`RADARR_URL` e `SONARR_URL` hanno valori interni Docker predefiniti. `BOT_LANGUAGE=it` è il default; è disponibile anche `en`.

La cache delle release manuali Arr è temporanea (circa 30 minuti): se un grab risponde che la release non è più valida, ripeti la ricerca e seleziona di nuovo.

## Sviluppo

```bash
docker run --rm -v "$PWD":/workspace -w /workspace maven:3.9-eclipse-temurin-21 ./mvnw test
docker run --rm -v "$PWD":/workspace -w /workspace maven:3.9-eclipse-temurin-21 ./mvnw package
docker build -t cineseekerr-arr:local .
```

Le API usate sono le API v3 pubbliche di Radarr/Sonarr: lookup, creazione senza auto-search, manual search `POST /release`, elenco cache `GET /release` e grab `POST /release` con `guid`/`indexerId`.
