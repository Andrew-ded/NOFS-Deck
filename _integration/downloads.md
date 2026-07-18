# Интеграция: «Вахтёр загрузок» (п.7 бэклога)

Готовые файлы (созданы, существующие не тронуты):
- `agent/NofsAgent/Services/DownloadsWatcherService.cs` — FileSystemWatcher на Downloads,
  active раз в секунду при росте, done однократно, OpenLast()/ShowLast().
  **Не скомпилируется, пока в Protocol.cs не появится DownloadMsg (блок 1).**
- `android/.../ui/components/DownloadCard.kt` — плашка-оверлей.
  **Не скомпилируется, пока в DeskState.kt не появится DownloadState (блок 3).**

Ниже — точные блоки для существующих файлов. camelCase на проводе, тип
сообщения `"download"`, state `"active" | "done"`.

---

## 1. `agent/NofsAgent/Protocol.cs`

В секцию `// ---------- агент -> планшет ----------`, после `ClaudeUsageMsg`:

```csharp
/// <summary>
/// Вахтёр загрузок. State "active" — браузер качает: FileName без
/// temp-расширения, SizeBytes растёт, Path пустой (файла ещё нет).
/// State "done" — файл готов: Path полный, по нему работают
/// команды openDownload/showDownload.
/// </summary>
public sealed record DownloadMsg(string State, string FileName, long SizeBytes, string Path)
{
    public string Type => "download";
}
```

Сигнатура зафиксирована — `DownloadsWatcherService` создаёт ровно
`new DownloadMsg("active"|"done", fileName, sizeBytes, path)`.

---

## 2. `android/.../net/Protocol.kt`

Зеркало в секцию `// ---------- агент -> планшет ----------`, после `ClaudeUsageMsg`:

```kotlin
/**
 * Вахтёр загрузок. state "active" — браузер качает (sizeBytes растёт,
 * path пустой); "done" — файл готов (path полный).
 */
@Serializable
data class DownloadMsg(
    val state: String = "",
    val fileName: String = "",
    val sizeBytes: Long = 0,
    val path: String = ""
)
```

В `object Cmd` (после `claudeCalibrate`) — простые команды без параметров,
агент сам помнит путь последнего файла:

```kotlin
    /** Открыть последний скачанный файл на ПК. */
    fun openDownload(): JsonObject = simple("openDownload")

    /** Показать последний скачанный файл в Explorer. */
    fun showDownload(): JsonObject = simple("showDownload")
```

---

## 3. `android/.../data/DeskState.kt`

Модель — рядом с `ClaudeUsage`:

```kotlin
/**
 * Вахтёр загрузок: транзиентная плашка. fileName пустой = плашки нет —
 * источник правды для видимости, как у FilePassportState.
 */
data class DownloadState(
    val state: String = "",        // "active" | "done"
    val fileName: String = "",
    val sizeBytes: Long = 0,
    val at: Long = 0L              // штамп прихода — для автозакрытия и dismiss
)
```

Поле в `DeskState` (после `claude`):

```kotlin
    val download: DownloadState = DownloadState()
```

Команды в `sealed interface DeskCommand` (после `ClaudeCalibrate`):

```kotlin
    /** Открыть последний скачанный файл на ПК. */
    data object OpenDownload : DeskCommand
    /** Показать последний скачанный файл в Explorer. */
    data object ShowDownload : DeskCommand
```

---

## 4. `android/.../net/WebSocketDeskDataSource.kt`

Импорт: `import com.nofs.desk.data.DownloadState`

Ветка в `handleMessage` (рядом с `"claude" ->`):

```kotlin
            "download" -> {
                val d = ProtocolJson.decodeFromJsonElement<DownloadMsg>(obj)
                _state.update {
                    it.copy(
                        download = DownloadState(
                            state = d.state,
                            fileName = d.fileName,
                            sizeBytes = d.sizeBytes,
                            at = System.currentTimeMillis()
                        )
                    )
                }
            }
```

Ветки в `send` (when исчерпывающий — без них не соберётся):

```kotlin
            DeskCommand.OpenDownload -> Cmd.openDownload()
            DeskCommand.ShowDownload -> Cmd.showDownload()
```

---

## 5. `android/.../data/FakeDeskDataSource.kt`

В `when (command)` внутри `send` — пустые обработчики:

```kotlin
            DeskCommand.OpenDownload -> { /* демо: открывать нечего */ }
            DeskCommand.ShowDownload -> { /* демо: показывать нечего */ }
```

---

## 6. `agent/NofsAgent/AgentHost.cs`

Поле (рядом с `_claude`):

```csharp
    private readonly DownloadsWatcherService _downloads;
```

Конструктор (после `_claude = new ClaudeUsageService(config);`) — подписка
до Start, событие само рейт-лимитировано сервисом:

```csharp
        _downloads = new DownloadsWatcherService();
        _downloads.Updated += msg => _ = _server.BroadcastAsync(msg);
```

`StartAsync` (после создания `_foreground`):

```csharp
        // Вахтёр загрузок: события файловой системы, не поллинг-цикл
        _downloads.Start();
```

`HandleCommandAsync` — case'ы перед `default:`:

```csharp
            case "openDownload": _downloads.OpenLast(); break;
            case "showDownload": _downloads.ShowLast(); break;
```

`Dispose` (рядом с `_audio.Dispose();`):

```csharp
        _downloads.Dispose();
```

Снапшот новому клиенту НЕ шлём: плашка транзиентная, показывать при
подключении вчерашний done незачем.

---

## 7. `android/.../ui/DeskScreen.kt` (TabletDeskScreen)

Импорты: `import com.nofs.desk.data.DeskCommand` уже есть; добавить
`import com.nofs.desk.ui.components.DownloadCard`.

Оверлей — в корневой Box, СРАЗУ ПОСЛЕ блока пилюли плеера. Окружающий код
для ориентира (вставка между пилюлей и SnackbarHost):

```kotlin
        // Чёрная пилюля плеера — внизу по центру, тап открывает плеер
        AnimatedVisibility(
            visible = hasMedia && playerProgress < 0.5f,
            enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp)
        ) {
            BottomPlayerPill(
                media = state.media,
                onTogglePlay = { viewModel.send(DeskCommand.TogglePlay) },
                onOpenPlayer = { playerOpen = true }
            )
        }

        // >>> ВСТАВКА: вахтёр загрузок — плашка внизу слева, не мешает пилюле.
        // Крестик гасит текущую плашку штампом; новое событие (свежий at)
        // показывает её снова.
        var downloadDismissedAt by remember { mutableLongStateOf(0L) }
        DownloadCard(
            download = state.download,
            visible = state.download.fileName.isNotBlank() &&
                state.download.at > downloadDismissedAt,
            onOpen = { viewModel.send(DeskCommand.OpenDownload) },
            onShow = { viewModel.send(DeskCommand.ShowDownload) },
            onDismiss = { downloadDismissedAt = System.currentTimeMillis() },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 10.dp)
        )
        // <<< КОНЕЦ ВСТАВКИ

        // Ошибки от агента
        SnackbarHost(
```

`remember` и `mutableLongStateOf` в DeskScreen.kt уже импортированы
(скринсейвер), `getValue`/`setValue` тоже. Телефонная раскладка
(PhoneDeskScreen) — сознательно не трогаем, плашка только на планшете.

---

## Проверка после сшивки

1. Агент собирается; в логе при старте `downloads watcher: C:\Users\...\Downloads`.
2. Скачать файл в Chrome: на планшете плашка с растущим «N МБ…», по
   завершении — «Открыть»/«Показать», крестик гасит, сама уходит через 15 с.
3. `openDownload`/`showDownload` с планшета открывают файл/Explorer на ПК.
4. Демо-режим: плашки нет (FakeDeskDataSource download не наполняет), команды
   глотаются молча.

## Риски (эвристика завершения)

- Done определяется по переименованию temp→финал (Chrome/Edge/Firefox) либо по
  «свежий не-temp файл в течение 5 с после исчезновения temp». Файл, вручную
  брошенный в Downloads в эти 5 с, может дать ложный done — принято как цена
  простоты.
- Safari/старые качальщики без temp-расширений не ловятся вовсе.
- `.tmp` ловят и не-браузерные записи в Downloads — возможны лишние active.
- Паузу загрузки планшет видит как «зависла» (размер не растёт — событий нет),
  отменённую — как молча пропавшую плашку active (active перестаёт приходить,
  done нет; плашка останется до следующего события или перезапуска приложения —
  если будет раздражать, добавить автоскрытие active по staleness `at`).
