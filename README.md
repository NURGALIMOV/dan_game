# АГЕНТСТВО — детектив-игра

Веб-игра-детектив. Игрок расследует убийство, изучая документы дела и допрашивая помощника. Демо бесплатно, полное расследование за 990 руб.

## Стек

- **Backend** — Java 21, Spring Boot 3.3, Spring Data JPA + Hibernate, MapStruct, Lombok, Liquibase
- **БД** — PostgreSQL 16
- **Frontend** — статический HTML + nginx (proxy для `/api/*`)
- **Сборка** — Gradle 8.10
- **Оркестрация** — Docker Compose

## Быстрый запуск (Docker, рекомендуемый путь)

Требования: Docker Desktop (или Docker Engine + Compose plugin).

```bash
docker compose up -d --build
```

Открыть **http://localhost** — игра доступна.

Логи:
```bash
docker compose logs -f          # всё
docker compose logs -f backend  # только backend
```

Остановка (БД сохраняется в volume):
```bash
docker compose down
```

Полная очистка вместе с данными:
```bash
docker compose down -v
```

## Топология контейнеров

```
Internet ──► frontend (nginx, :80)
                │
                └─ /api/*  ──► backend (Spring Boot, internal)
                                 │
                                 └─ jdbc ──► db (postgres, internal, volume db_data)
```

Извне виден **только** `frontend:80`. Backend и БД — без проброса портов, доступ только из internal-сети Docker.

## Конфигурация (опционально)

Создай `.env` в корне для переопределения дефолтов:

```env
POSTGRES_DB=detective
POSTGRES_USER=detective
POSTGRES_PASSWORD=измени_меня
FRONTEND_PORT=80
```

Все переменные имеют дефолты в `docker-compose.yml`, без `.env` тоже работает.

## Локальная сборка backend (без Docker)

Требуется: JDK 21 и запущенный PostgreSQL на `localhost:5432` с базой `detective` и пользователем `detective:detective_pass`.

```bash
cd backend
./gradlew bootJar           # собрать fat-jar в build/libs/
./gradlew bootRun           # запустить сразу из исходников
```

Backend поднимется на `http://localhost:8000`. Liquibase сам накатит миграции при старте.

## Развёртывание (production)

1. На сервере должен быть установлен Docker + Compose plugin.
2. Скопировать репозиторий: `git clone ...`
3. Создать `.env` с production-значениями (важно: задать `POSTGRES_PASSWORD`).
4. Поднять стек: `docker compose up -d --build`
5. Поставить reverse-proxy (nginx/Traefik/Caddy) перед `frontend:80` для TLS.

Volume `dan_game_db_data` хранится на хосте под управлением Docker — переживает перезапуски и пересборки. Бэкап: `docker run --rm -v dan_game_db_data:/data -v $(pwd):/backup alpine tar czf /backup/db.tgz -C /data .`

## Структура проекта

```
.
├── docker-compose.yml          ← оркестрация: db + backend + frontend
├── backend/                    ← Spring Boot модуль
│   ├── Dockerfile              ← multi-stage: gradle build → temurin jre
│   ├── build.gradle            ← deps: data-jpa, mapstruct, lombok, liquibase, postgresql
│   └── src/main/
│       ├── java/com/dangame/detective/
│       │   ├── controller/     ← REST endpoints, @Transactional
│       │   ├── entity/         ← JPA @Entity (UserEntity, ProgressEntity, AchievementEntity)
│       │   ├── repo/           ← Spring Data JPA интерфейсы
│       │   ├── mapper/         ← MapStruct: entity → DTO
│       │   ├── dto/            ← request/response records
│       │   ├── gamedata/       ← Cases.java — игровые данные CASE_001
│       │   └── security/       ← @CurrentUser + Bearer token resolver
│       └── resources/
│           ├── application.yml
│           └── db/changelog/   ← Liquibase: master + changes/001-003
└── frontend/                   ← nginx модуль
    ├── Dockerfile
    ├── nginx.conf              ← / → static, /api/* → backend:8000
    └── index.html              ← single-page приложение
```

## Миграции БД (Liquibase)

Все changeset'ы в `backend/src/main/resources/db/changelog/changes/`. Master-файл подтягивает их через `includeAll`.

Чтобы добавить новую миграцию — создай `004-<название>.yaml` рядом с существующими:

```yaml
databaseChangeLog:
  - changeSet:
      id:     004-add-something
      author: <твой-ник>
      changes:
        - addColumn:
            tableName: users
            columns:
              - column:
                  name: avatar_url
                  type: TEXT
```

Master трогать не нужно — `includeAll` подхватит файл при следующем старте backend.
