# NOFS Desk — контекст для агента

## Что это
Планшет-компаньон: панель управления Windows-ПК на Android-планшете (Teclast P40HD,
Unisoc T606, альбомная ориентация). Проект собран целиком: Android-приложение
(демо-режим + реальный WebSocket-источник) и .NET-агент в трее на ПК.

## Стек и ограничения
- Планшет: Kotlin + Jetpack Compose, Material 3.
- `minSdk 26`, `targetSdk 34`, Kotlin 2.0.21, AGP 8.5.2, Compose BOM 2024.09.03.
- Kotlin Compose Compiler через плагин `org.jetbrains.kotlin.plugin.compose`.
- Шрифты Manrope + JetBrains Mono **забандлены** в `res/font` (оффлайн).
- Иконки: `material-icons-extended`. Сеть: OkHttp (WebSocket) + kotlinx-serialization.
- Агент: C# / .NET 8, `net8.0-windows10.0.19041.0`, WinForms-трей,
  LibreHardwareMonitorLib. Запуск от админа (температуры + порт HttpListener).
- UI-подписи на русском; идентификаторы/логи — латиница.

## Текущее состояние
UI: пастельный флэт, мини-плеер, чёрный плеер выезжает справа с морфингом обложки
(круг→скруглённый квадрат) — **на месте правой колонки (Git-панели)**, подменяя её.
Левая колонка помещается БЕЗ вертикального скролла (карточки макросов компактные,
шапка статичная — collapse=0). Сетка метрик — 3 карточки (CPU/GPU/RAM).
Макросы контекстные: секция «Макросы · <активное приложение>» (хоткеи Word/PowerPoint/
Excel/Rider/Studio/Chrome из config.json, поле app) + секция «Система» (скриншот,
терминал, проводник, ночной свет, тишина, блокировка, сон). Панель Git свопается
(таб + свайп): «Локально» и «GitHub»; прячется кнопкой » (справа от табов),
возвращается узкой ручкой у правого края.

Связь с ПК реализована: `WebSocketDeskDataSource` (реконнект с бэкоффом, кэш обложки),
UDP-автопоиск агента, настройки в `SettingsDialog` (демо-режим / IP+порт / поиск).
Демо-режим (`FakeDeskDataSource`) остаётся источником по умолчанию.
Мини-плеер и плеер скрываются, когда на ПК нет медиа-сессии (пустой title).
Git-панель — полноценный клиент: история до 200 коммитов всех веток с графом
(`GitGraph.kt`, раскладка по дорожкам), метки refs, переключение веток
(дропдаун → checkout), Pull/Commit/Push.

Агент (`agent/NofsAgent`): WebSocket-сервер (HttpListener, `/ws`), UDP-ответчик
автопоиска, метрики LHM (1 с), медиа через GSMTC с обложкой base64 только при смене
трека (1 с; позиция экстраполируется по LastUpdatedTime), контекст (2 с) — чипы
ТОЛЬКО запущенных приложений из watchlist (Office/Rider/Studio/Chrome), макросы
(run/keys/lock/sleep/mute из config.json), git CLI (полный лог `--all` с parents+refs,
ветки, checkout; pull/commit/push с флагом busy), GitHub REST (PR+Issues, токен
опционален). Трей: статус подключения, конфиг, лог. Контекстное меню Проводника
«Отправить на планшет (NOFS)» (HKCU, регистрируется на старте): папка становится
git-репо на лету — второй экземпляр exe шлёт GET /set-repo работающему агенту,
выбор сохраняется в config.json.

## Архитектура (ключевой шов)
`DeskDataSource` (интерфейс) → `DeskViewModel` (выбирает Fake/WebSocket по настройкам,
отдаёт `StateFlow<DeskState>`) → `DeskScreen`. UI и модели от источника не зависят.

Протокол: один WebSocket, JSON с полем `type`. Агент→планшет: hello, metrics, media,
context, macros, git (log с parents+refs, branches, repoName), github.
Планшет→агент: `{"type":"cmd","cmd":...}` (togglePlay, next, prev, seek, runMacro,
focusApp, gitRefresh, gitPull, gitCommit, gitPush, gitCheckout(branch),
githubRefresh). Метрики/медиа — push 1 с; контекст — 2 с; git/github — по запросу.
Плюс локальный HTTP GET `/set-repo?path=…` (только localhost) — смена репозитория.
**C#-модели `agent/NofsAgent/Protocol.cs` — зеркало `app/.../net/Protocol.kt`,
менять строго синхронно.**

## Карта файлов
```
app/src/main/java/com/nofs/desk/
  MainActivity.kt                 фуллскрин-иммерсив, альбомная
  DeskViewModel.kt                выбор источника (демо/WebSocket) по настройкам
  data/
    DeskState.kt                  модели: Metric, MediaState, Macro, AppContext, GitState (+GitHubPullRequest, GitHubIssue), DeskState, DeskCommand
    DeskDataSource.kt             ИНТЕРФЕЙС-ШОВ
    FakeDeskDataSource.kt         демо: живые данные + обработка команд
    DeskSettings.kt               настройки (SharedPreferences)
  net/
    Protocol.kt                   JSON-протокол (зеркало Protocol.cs!)
    WebSocketDeskDataSource.kt    реальный источник: реконнект, кэш обложки
    Discovery.kt                  UDP-автопоиск (NOFS_DISCOVER/NOFS_HERE, порт 48485)
  ui/
    DeskScreen.kt                 сборка экрана (колонки 1.7 : 1), SettingsDialog
    theme/Color.kt Type.kt Theme.kt
    components/
      HeaderAndMiniPlayer.kt      DeskHeader (сворачивается, чип статуса) + MiniPlayer
      MetricsGrid.kt              3 карточки (CPU/GPU/RAM)
      MacroPanel.kt               чипы приложений + сетка макросов
      GitPanel.kt                 своп Local/GitHub, дропдаун веток, граф истории
      GitGraph.kt                 раскладка графа коммитов по дорожкам + Canvas
      PlayerSheet.kt              чёрный плеер в слоте Git-панели + морфинг обложки
      SettingsDialog.kt           демо-режим / IP+порт / автопоиск
      MacroIcons.kt               строковый ключ -> ImageVector

agent/NofsAgent/                  .NET 8 трей-агент (см. agent/README.md)
  Program.cs TrayContext.cs       бутстрап + трей (+ --set-repo из Проводника)
  ExplorerMenu.cs                 пункт меню Проводника (HKCU)
  Config.cs config.json           конфиг: порты, repoPath, github, apps, macros
  Protocol.cs                     DTO (зеркало Protocol.kt!)
  AgentHost.cs                    склейка: циклы push + диспетчер команд
  Net/WsServer.cs                 HttpListener WebSocket, broadcast
  Net/DiscoveryResponder.cs       UDP-автопоиск
  Services/MetricsService.cs      LibreHardwareMonitor
  Services/MediaService.cs        GSMTC: трек/обложка/управление
  Services/ContextService.cs      активное окно + focusApp
  Services/MacroService.cs        run:/keys:/lock/sleep/mute (SendInput)
  Services/GitService.cs          git CLI + «N назад» по-русски
  Services/GitHubService.cs       REST: PR + Issues
```

## Дизайн-токены (держать консистентность)
- Фон `#F6F4F0`, карточка `#FFFFFF`, текст `#2B2B28`, приглушённый `#9A968C`, ручка `#DDD8CE`.
- Плеер: фон `#0E0E10`, текст `#F4F3F0`, карточка `#1A1A1E`.
- Пастельные акценты категорий (bg/bar): sage, peach, sky, lavender, sand, rose — в `Color.kt`.
- Радиусы крупные (18–24dp), без свечений/теней-«подсветки», строго.
- Шрифты: Manrope (UI), JetBrains Mono (числа/хэши/время).

## Известные грабли
- **Путь Android-проекта не должен содержать кириллицу** — AGP на Windows падает.
  Держать в ASCII-пути (напр. `C:\dev\NOFSDesk`). Обход: `android.overridePathCheck=true`.
- Агент без прав админа: температуры нулевые (планшет скрывает), для порта нужен
  `netsh http add urlacl url=http://+:48484/ user=Все`.
- Android-сборка в среде-заготовке не проверялась компилятором — возможны точечные
  API-мелочи Compose, правятся по тексту ошибки. Агент собирается чисто (dotnet build).
- Обе стороны протокола менять синхронно (Protocol.kt ↔ Protocol.cs).

## Что дальше (роадмап)
1. Схлопывание макросов в вертикальную колонку из 4 иконок при открытии плеера
   (сейчас упрощено до лёгкого масштабирования контента). Сворачивание шапки
   по скроллу отключено (левая зона без скролла, collapse=0) — можно вернуть,
   завязав на открытие плеера.
2. Per-app микшер громкости (Core Audio на агенте + новая страница/панель).
3. Цветовой фидбэк статуса билда (макрос build → push статуса на планшет).
4. Тюнинг реконнекта при смене Wi-Fi; сжатие обложки на агенте при >500 КБ.
