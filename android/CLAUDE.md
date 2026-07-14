# NOFS Desk — контекст для агента

## Что это
Панель управления Windows-ПК на Android. Основное устройство — планшет
(Teclast P40HD, Unisoc T606, жёстко альбомная ориентация); отдельно есть
урезанный экран для телефона (портрет+альбом, см. ниже). Проект собран
целиком: Android-приложение (демо-режим + реальный WebSocket-источник)
и .NET-агент в трее на ПК.

## Ветка feature/companion-suite (в работе)
Большой набор новых фич из бэклога (10 шт), идём по списку. Коммитить и пушить
ТОЛЬКО с ПК: из песочницы git берёт файлы с отставанием и портит коммиты.
Собрать/проверить в песочнице тоже нельзя — dotnet вымывается из /tmp, а bash-
монтирование отстаёт от реального диска Windows (Edit/Read работают с реальным W:\,
их и видит Android Studio). Новые UI-панели используют `LocalDeskPalette.current`.

ГОТОВО (не проверено компилятором — собрать обе стороны на ПК):
- **Сцена «Тень билда» (ф.7+10)**: протокол `scene` (phase idle/running/external/
  success/failed) + `daily` + `builds`; агент `Services/BuildService.cs` (запуск
  сборки из config `builds[]`, парсинг gradle `> Task`/dotnet, счётчики тестов,
  число задач из прошлой сборки; cmd-обёртки получают `chcp 65001` для UTF-8;
  проверка cwd), `Services/DailyService.cs` (сводка дня: сборки, случайный коммит,
  TODO/FIXME греп), детекция сборок из IDE в `AgentHost.ExternalBuildLoopAsync`
  (CPU gradle-демона/MSBuild, опрос 1с); планшет `ui/components/SceneOverlay.kt`
  (тёмная сцена, прогресс, счётчики, хвост лога, вспышка success/fail, автозакрытие
  10с), кнопки запуска над git-логом, idle-сводка второй строкой на скринсейвере.
  Демо-прогон сцены — по кнопке (FakeDeskDataSource.runDemoBuild).
- Строка полного пути к папке репо/сборки под табами Локально/GitHub (git.repoPath).
- «Отправить на планшет» теперь принимает папку БЕЗ .git (задаёт и git-панель,
  и cwd сборок).
- **Remote typing (ф.2)**: агент `Services/RemoteTypeService.cs` — низкоуровневый
  хук `WH_KEYBOARD_LL` на своём STA-потоке (message pump, иначе хук не работает
  надёжно); тоггл-хоткей из `config.json` (`remoteType.hotkey`,
  по умолчанию Scroll Lock) включает режим, глотает ВСЕ нажатия (до активного
  окна ПК не долетают), транслирует их в текст через `ToUnicodeEx` со своей
  копией keyState (реальные Shift/Ctrl/Alt/CapsLock не обновляются, т.к. ОС
  нажатия не видит) и раскладкой окна, которое было в фокусе на момент включения
  (важно для кириллицы). Спецклавиши (backspace/tab/enter/delete/стрелки/home/end)
  идут отдельным `kind:"special"`. Escape — запасной off без хоткея. Протокол:
  `remoteTypeState` (вкл/выкл) + `remoteKey` (char/special) агент→планшет,
  `remoteTypeStop` планшет→агент; авто-выключение при обрыве связи с планшетом.
  Планшет: `DeskState.remoteTypeActive/remoteTypeBuffer`, буфер собирается в
  `WebSocketDeskDataSource` (char append / backspace; enter/tab/стрелки пока
  не обрабатываются — вне MVP), `GitPanel.kt` — плашка-уведомление + диалог
  коммита показывает `readOnly`-поле, зеркалящее буфер, с кнопкой остановки.
- **Телефонный экран**: `DeskScreen()` — точка входа-диспетчер, роутит по
  `LocalConfiguration.smallestScreenWidthDp >= 600` (Material-порог планшета)
  на `TabletDeskScreen` (прежний полный layout, без изменений) или
  `PhoneDeskScreen.kt` (новый). Ориентация: `MainActivity` жёстко ставит
  `SCREEN_ORIENTATION_LANDSCAPE` для планшета и `UNSPECIFIED` (свободный
  поворот) для телефона; манифест больше не фиксирует `screenOrientation`.
  Телефонный экран — урезанный набор данных: БЕЗ метрик CPU/GPU/RAM, БЕЗ
  строки контекста (чипы приложений), БЕЗ звука/плейтайма. Остаётся: шапка
  (часы/статус/настройки, без игрового режима и кнопки Git), макросы одним
  плоским скроллящимся гридом без чипов-переключателей (`PhoneMacroPanel`
  в `MacroPanel.kt` — активное приложение + системные вместе), Git —
  урезанная `ui/components/PhoneGitPanel.kt` (БЕЗ Pull/Commit/Push/веток:
  только кнопки сборки на прежнем месте + граф коммитов, свайп → PR/Issues
  с GitHub, тап по строке открывает ссылку в браузере через `Intent`,
  GitHub подтягивается автоматически при показе, не по свайпу как на
  планшете), плеер — тот же `PlayerSheet`/`BottomPlayerPill`, что на
  планшете, без изменений. Сцена «Тень билда» (`SceneOverlay`) общая с
  планшетом, кнопки её запуска живут в `PhoneGitPanel`. Раскладка:
  альбом — `Row` (макросы слева, Git+плеер справа, слот-паттерн как у
  планшета), портрет — `Column` (макросы сверху, Git+плеер снизу);
  переключение — `PhoneBody` внутри `PhoneDeskScreen.kt` читает
  `LocalConfiguration.orientation`. `PagerDots` в `RightPanel.kt` сделан
  публичным для переиспользования в `PhoneGitPanel`.

ЗНАЮ ПРО НЕДОДЕЛКИ:
- Телефонный экран: не проверен компилятором и НЕ ПРОВЕРЕН ВИЗУАЛЬНО (нет
  Android-эмулятора/устройства в песочнице) — только код-ревью. При смене
  ориентации `PhoneBody` переключается между `Row`/`Column`, структурно
  разными поддеревьями — локальный `remember`-стейт внутри них (например,
  текущая страница пейджера в `PhoneGitPanel`) на повороте сбрасывается,
  это ожидаемо и не чинилось. Открытие ссылок PR/Issues не проверено на
  реальном устройстве.
- Remote typing: работает только с полем сообщения коммита (единственная цель
  из бэклога); курсор/выделение не поддерживаются — только дозапись символа и
  backspace с конца. `enter`/`tab`/стрелки/`home`/`end` шлются с ПК, но пока
  игнорируются буфером на планшете. Хоткей — тоггл (нажал/начал печатать/нажал
  снова), а не удержание, как буквально написано в исходной идее «push-to-talk»:
  печатать двумя руками и одновременно удерживать хоткей физически неудобно.
  Не проверено компилятором — собрать агента и планшет на ПК.
- Счётчики тестов в сцене наполняются только если сборка реально гоняет тесты и
  печатает парсимую сводку. dotnet test — `Failed: N, Passed: N` ловится; gradle
  `assembleDebug` тесты не гоняет, а `test` печатает `N tests completed, M failed`
  — этот формат парсер пока НЕ ловит. Нужно доработать regex + добавить в config
  сборку с тестами. Автотестов у самого проекта (агент C#, планшет Kotlin) нет.

УБРАНО: **QR-мост буфера (ф.8)** — был реализован (агент ClipboardService.cs,
протокол `clipboard`, планшет QrOverlay.kt на ZXing), но идею признали
неактуальной и весь код убрали (агент/планшет/зависимость zxing-core).

ОСТАЛОСЬ (задачи): паспорт файла (ф.9), Obsidian (ф.1), git-дифф+«пока меня не
было» (ф.4), логи (ф.5), предиктивные алерты (ф.3), presence (ф.6).

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
  MainActivity.kt                 фуллскрин-иммерсив; ориентация по типу устройства (см. выше)
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
    DeskScreen.kt                 диспетчер планшет/телефон + TabletDeskScreen (колонки 1.7:1), SettingsDialog
    PhoneDeskScreen.kt            телефонный экран: урезанные данные, портрет(Column)/альбом(Row)
    theme/Color.kt Type.kt Theme.kt
    components/
      HeaderAndMiniPlayer.kt      DeskHeader (часы, GameModeButton, чип статуса) + MiniPlayer
      MetricsGrid.kt              3 карточки (CPU/GPU/RAM)
      MetricSparks.kt             компактный режим метрик — спарклайны у часов
      MacroPanel.kt               чипы приложений + сетка макросов; PhoneMacroPanel — плоский грид без чипов
      PhoneGitPanel.kt            телефон: кнопки сборки + граф коммитов, свайп → GitHub PR/Issues со ссылками
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
  Services/RemoteTypeService.cs   WH_KEYBOARD_LL хук: клавиатура ПК -> планшет
```

## Дизайн-токены (держать консистентность)
- Фон `#F6F4F0`, карточка `#FFFFFF`, текст `#2B2B28`, приглушённый `#9A968C`