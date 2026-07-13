# NOFS Desk — контекст для агента

## Что это
Планшет-компаньон: панель управления Windows-ПК на Android-планшете (Teclast P40HD,
Unisoc T606, альбомная ориентация). Проект собран целиком: Android-приложение
(демо-режим + реальный WebSocket-источник) и .NET-агент в трее на ПК.

## Ветка feature/companion-suite (в работе)
Большой набор новых фич из бэклога, идём по списку. Готово: **QR-мост буфера**
(агент `ClipboardService.cs` — STA-поток + AddClipboardFormatListener, фильтр
чувствительных строк; протокол `clipboard`; планшет `QrOverlay.kt` — ZXing,
транзиентная карточка снизу-справа с кольцом-таймером, тап = крупный QR).
Осталось (см. задачи): сцена/тень билда, паспорт файла, Obsidian, git-дифф+
«пока меня не было», логи, предиктивные алерты, presence, remote typing.
Новые UI-панели используют `LocalDeskPalette.current` (игровой тёмный режим).

## Стек и ограничения
- Планшет: Kotlin + Jetpack Compose, Material 3.
- `minSdk 26`, `targetSdk 34`, Kotlin 2.0.21, AGP 8.5.2, Compose BOM 2024.09.03.
- Kotlin Compose Compiler через плагин `org.jetbrains.kotlin.plugin.compose`.
- Шрифты Manrope + JetBrains Mono **забандлены** в `res/font` (оффлайн).
- Иконки: `material-icons-extended`. Сеть: OkHttp (WebSocket) + kotlinx-serialization.
- Агент: C# / .NET 8, `net8.0-windows10.0.19041.0`, WinForms-трей,
  LibreHardwareMonitorLib. Работает без прав админа (сервер на TcpListener,
  не http.sys); от админа — только опционально, ради температур CPU/GPU.
- UI-подписи на русском; идентификаторы/логи — латиница.

## Текущее состояние
UI: пастельный флэт, мини-плеер (появляется/исчезает с анимацией, скрыт без
медиа-сессии), чёрный плеер выезжает справа с морфингом обложки (круг→скруглённый
квадрат) — **на месте правой колонки (Git-панели)**, подменяя её. Левая колонка
БЕЗ вертикального скролла (шапка статичная — collapse=0).

Метрики: 3 карточки (CPU/GPU/RAM); круглая кнопка рядом сворачивает их в компакт —
мягкие спарклайны справа от часов (`MetricSparks.kt`: история ~48с на планшете,
подпись CPU/GPU/RAM + значение %, для RAM — ГБ); тап по спарклайну разворачивает.

Макросы: кнопки КВАДРАТНЫЕ в адаптивной сетке `GridCells.Adaptive(88dp)` — при
спрятанном Git в ряд влезает больше (кнопки не растягиваются). Показывается ОДИН
набор: макросы активного приложения, либо «Система» — если выбран чип «Система»
(локально, ничего не фокусирует на ПК) или активное приложение не определено.
Чипы: запущенные приложения + «Система»; смена набора — кроссфейд AnimatedContent.

Правый слот — три страницы (`RightPanel.kt`, иконки-табы + кнопка »» спрятать,
возврат круглой кнопкой у чипа ПК): **Git** (как раньше: Локально/GitHub),
**Звук** (микшер, см. ниже), **Время** (плейтайм-трекер: агент копит время
активного окна по watchlist в playtime.json, планшет показывает сегодня/7 дней
с полосками).

Микшер (`MixerPanel` в `RightPanel.kt`): каналы (мастер-громкость + per-app
сессии через Core Audio/NAudio на агенте, пуш раз в 2 с) листаются ГОРИЗОНТАЛЬНО
свайпом (`HorizontalPager`) — по одному каналу на экран. Каждый канал — большой
ВЕРТИКАЛЬНЫЙ фейдер (`VerticalSlider` — стандартный Material3 `Slider`, повёрнутый
на 90° через `graphicsLayer`+`layout`; вверх = громче) и большая кнопка мьюта
на всю ширину под фейдером (56dp). Точки-индикатор страницы снизу. Отдельно
сверху — крупная кнопка мьюта микрофона (не листается, привязана к ПК целиком).

Игровой режим (`gameMode` в `DeskScreen`): отдельная кнопка в шапке
(`GameModeButton`, иконка геймпада) переключает ПОЛНОСТЬЮ отдельный режим —
тёмная палитра на весь экран (`DarkDeskPalette`, переиспользует токены плеера
`PlayerBg/Card/Text/Muted`, `LocalDeskPalette` в `Color.kt` прокидывается через
`CompositionLocalProvider` в `DeskScreen`), Git-панель недоступна (правый слот
всегда занят `MixerPanel` — без вкладок, без кнопки «спрятать»), громкость —
на первом плане. GitPanel/PlaytimePanel тёмную тему не поддерживают (не
рендерятся в игровом режиме, поэтому и не нужно).

Скринсейвер (`Screensaver.kt`): чёрный экран с большими часами по бездействию
(таймаут в SettingsDialog, по умолчанию 10 мин, 0 = выкл), сброс любым касанием,
часы дрейфуют раз в минуту (анти-выгорание). Экран держится включённым
(FLAG_KEEP_SCREEN_ON в MainActivity).

Игровой сетап: игры добавляются в apps конфига агента + макросы с app=id игры
(пример Elden Ring в config.json). Протокол: + audio, playtime сообщения и
команды audioMaster/audioMuteMaster/audioMuteMic/audioSession/audioMuteSession.

Связь с ПК реализована: `WebSocketDeskDataSource` (реконнект с бэкоффом, кэш обложки),
UDP-автопоиск агента, настройки в `SettingsDialog` (демо-режим / IP+порт / поиск).
Демо-режим (`FakeDeskDataSource`) остаётся источником по умолчанию.
Мини-плеер и плеер скрываются, когда на ПК нет медиа-сессии (пустой title).

Медиа умеет ещё один источник — `LocalMediaSource` (`media/`): локальная
Android-медиасессия устройства через `MediaSessionManager` + пустой
`NofsNotificationListener` (нужен вручную выданный доступ к уведомлениям,
диплинк на него — в `SettingsDialog`). `DeskViewModel` мёржит её с ПК-медиа
автоматически: ПК в приоритете, локальная сессия — только когда на ПК
реально ничего не играет (сценарий: звук с планшета уходит на ПК через adb,
своей GSMTC-сессии там нет). Команды плеера роутятся туда же, откуда взят
текущий трек. `MediaState.isLocalSource` — для маленькой иконки-подсказки
в `BottomPlayerPill`/`PlayerSheet`, откуда сейчас играет.
Git-панель — полноценный клиент: история до 200 коммитов всех веток с графом
(`GitGraph.kt`, раскладка по дорожкам), метки refs, переключение веток
(дропдаун → checkout), Pull/Commit/Push.

Агент (`agent/NofsAgent`): WebSocket-сервер (TcpListener + ручной хендшейк, `/ws`,
без прав админа), UDP-ответчик
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
  DeskViewModel.kt                выбор источника (демо/WebSocket) + мёрж медиа с LocalMediaSource
  data/
    DeskState.kt                  модели: Metric, MediaState (+isLocalSource), Macro, AppContext, GitState (+GitHubPullRequest, GitHubIssue), DeskState, DeskCommand
    DeskDataSource.kt             ИНТЕРФЕЙС-ШОВ
    FakeDeskDataSource.kt         демо: живые данные + обработка команд
    DeskSettings.kt               настройки (SharedPreferences)
  media/
    NofsNotificationListener.kt   пустой NotificationListenerService — доступ MediaSessionManager'а
    LocalMediaSource.kt           локальная медиа-сессия устройства (запасной источник плеера)
  net/
    Protocol.kt                   JSON-протокол (зеркало Protocol.cs!)
    WebSocketDeskDataSource.kt    реальный источник: реконнект, кэш обложки
    Discovery.kt                  UDP-автопоиск (NOFS_DISCOVER/NOFS_HERE, порт 48485)
  ui/
    DeskScreen.kt                 сборка экрана (колонки 1.7 : 1), SettingsDialog
    theme/Color.kt Type.kt Theme.kt
    components/
      HeaderAndMiniPlayer.kt      DeskHeader (часы, GameModeButton, чип статуса) + MiniPlayer
      MetricsGrid.kt              3 карточки (CPU/GPU/RAM)
      MetricSparks.kt             компактный режим метрик — спарклайны у часов
      MacroPanel.kt               чипы приложений + сетка макросов
      RightPanel.kt               табы Git/Звук/Время; MixerPanel — фейдеры каналов (пейджер)
      GitPanel.kt                 своп Local/GitHub, дропдаун веток, граф истории
      GitGraph.kt                 раскладка графа коммитов по дорожкам + Canvas
      PlayerSheet.kt              чёрный плеер в слоте правой колонки + морфинг обложки
      Screensaver.kt              чёрный экран по бездействию
      SettingsDialog.kt           демо-режим / IP+порт / автопоиск
      MacroIcons.kt               строковый ключ -> ImageVector

agent/NofsAgent/                  .NET 8 трей-агент (см. agent/README.md)
  Program.cs TrayContext.cs       бутстрап + трей (+ --set-repo из Проводника)
  ExplorerMenu.cs                 пункт меню Проводника (HKCU)
  Config.cs config.json           конфиг: порты, repoPath, github, apps, macros
  Protocol.cs                     DTO (зеркало Protocol.kt!)
  AgentHost.cs                    склейка: циклы push + диспетчер команд
  Net/WsServer.cs                 TcpListener + ручной WS-хендшейк, broadcast (без прав админа)
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
- Плеер / игровой режим (тёмная палитра): фон `#0E0E10`, текст `#F4F3F0`, карточка `#1A1A1E`,
  приглушённый `#8A8A92`, ручка `#2A2A30`.
- `DeskPalette` (`Color.kt`) — bg/card/text/muted/handle; `LightDeskPalette`/`DarkDeskPalette`,
  читаются через `LocalDeskPalette.current` (CompositionLocal, DeskScreen подставляет нужную
  в зависимости от `gameMode`). Новые компоненты левой колонки/микшера должны брать цвета
  через палитру, а не хардкодить `DeskCard`/`DeskText` напрямую — иначе не потемнеют в игровом
  режиме. GitPanel/PlaytimePanel — исключение (не видны в игровом режиме, оставлены светлыми).
- Пастельные акценты категорий (bg/bar): sage, peach, sky, lavender, sand, rose — в `Color.kt`.
- Радиусы крупные (18–24dp), без свечений/теней-«подсветки», строго.
- Шрифты: Manrope (UI), JetBrains Mono (числа/хэши/время).

## Известные грабли
- **Путь Android-проекта не должен содержать кириллицу** — AGP на Windows падает.
  Держать в ASCII-пути (напр. `C:\dev\NOFSDesk`). Обход: `android.overridePathCheck=true`.
- Агент без прав админа: только температуры CPU/GPU нулевые (планшет скрывает).
  Порт открывается штатным TcpListener — админ/netsh не нужны.
- Android-сборка в среде-заготовке не проверялась компилятором — возможны точечные
  API-мелочи Compose, правятся по тексту ошибки. Агент собирается чисто (dotnet build).
- `LocalMediaSource` без выданного доступа к уведомлениям просто молчит (не крашится) —
  доступ Android даёт выдать только вручную через системные настройки, диплинком
  (`Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`), runtime-permission-диалога нет.
- Обе стороны протокола менять синхронно (Protocol.kt ↔ Protocol.cs).

## Что дальше (роадмап)
1. Схлопывание макросов в вертикальную колонку из 4 иконок при открытии плеера
   (сейчас упрощено до лёгкого масштабирования контента). Сворачивание шапки
   по скроллу отключено (левая зона без скролла, collapse=0) — можно вернуть,
   завязав на открытие плеера.
2. Цветовой фидбэк статуса билда (макрос build → push статуса на планшет).
3. Тюнинг реконнекта при смене Wi-Fi; сжатие обложки на агенте при >500 КБ.
4. Игровой режим сейчас только переключает тему/слот на планшете — можно
   синхронизировать с ПК (например, макрос «включить режим игры» на агенте:
   DND, профиль звука) при входе/выходе.
