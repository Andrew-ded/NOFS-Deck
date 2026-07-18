# Интеграция: зеркало диалогов (ошибки + прогресс копирования)

Фича пп.10+11 бэклога. Новые файлы уже на месте, существующие НЕ трогались:

- `agent/NofsAgent/Services/DialogWatcherService.cs` — хуки #32770 через общий
  HookThread; ветка «ошибка» (скрин PrintWindow → base64 JPEG) и ветка
  «копирование» (classic-бар msctls_progress32, опрос 500 мс). Событие
  `Action<DialogMsg>? Updated`.
- `android/.../ui/components/DialogMirrorCard.kt` — оверлей: карточка ошибки
  со скрином (тап — полноэкранно, автоскрытие 30 с) и плашка копирования
  с прогрессом (100% → уходит через 2 с).

Ниже — точные блоки для склейки. Команд планшет → агент у фичи НЕТ,
только поток агент → планшет.

---

## 1. `agent/NofsAgent/Protocol.cs`

Добавить в секцию `// ---------- агент -> планшет ----------`
(например, после `ClaudeUsageMsg`). `DialogWatcherService.cs` ссылается на
этот тип — агент не соберётся, пока блок не добавлен:

```csharp
/// <summary>
/// Зеркало диалога ПК. kind = "error": всплыло окно ошибки — Title + скрин
/// окна (JPEG base64, может быть null, если снять не вышло), ProgressPct = -1.
/// kind = "copy": прогресс копирования Explorer — ProgressPct 0..100
/// (100 = диалог закрылся, планшет прячет плашку), ImageBase64 = null.
/// </summary>
public sealed record DialogMsg(string Kind, string Title, string? ImageBase64, int ProgressPct)
{
    public string Type => "dialog";
}
```

## 2. `android/app/src/main/java/com/nofs/desk/net/Protocol.kt`

Зеркальный DTO в секцию `// ---------- агент -> планшет ----------`
(после `ClaudeUsageMsg`). Провод — camelCase, как везде:

```kotlin
/**
 * Зеркало диалога ПК. kind = "error": окно ошибки — title + скрин (JPEG
 * base64, null = снять не вышло), progressPct = -1. kind = "copy": прогресс
 * копирования Explorer 0..100 (100 = диалог закрылся).
 */
@Serializable
data class DialogMsg(
    val kind: String = "",
    val title: String = "",
    val imageBase64: String? = null,
    val progressPct: Int = -1
)
```

## 3. `android/app/src/main/java/com/nofs/desk/data/DeskState.kt`

Добавить состояние (рядом с `ClaudeUsage`, перед `data class DeskState`):

```kotlin
/**
 * Зеркало диалога ПК (ошибка/копирование). at — штамп прихода события:
 * по нему UI решает «свежее или уже закрыто» и перезапускает таймеры.
 * at == 0 — событий ещё не было, оверлей не показываем.
 */
data class DialogMirrorState(
    val kind: String = "",
    val title: String = "",
    val imageBase64: String? = null,
    val progressPct: Int = -1,
    val at: Long = 0L
)
```

И поле в `DeskState` (в конец списка, после `claude`):

```kotlin
    val claude: ClaudeUsage = ClaudeUsage(),
    val dialog: DialogMirrorState = DialogMirrorState()
```

Команд у фичи нет — `DeskCommand` не трогаем.

## 4. `android/app/src/main/java/com/nofs/desk/net/WebSocketDeskDataSource.kt`

Импорт:

```kotlin
import com.nofs.desk.data.DialogMirrorState
```

Ветка в `when (peekType(obj))` внутри `handleMessage` (рядом с `"claude"`):

```kotlin
            "dialog" -> {
                val d = ProtocolJson.decodeFromJsonElement<DialogMsg>(obj)
                _state.update {
                    it.copy(
                        dialog = DialogMirrorState(
                            kind = d.kind,
                            title = d.title,
                            imageBase64 = d.imageBase64,
                            progressPct = d.progressPct,
                            at = System.currentTimeMillis()
                        )
                    )
                }
            }
```

## 5. `agent/NofsAgent/AgentHost.cs`

Поле (к остальным сервисам):

```csharp
    private readonly DialogWatcherService _dialogs = new();
```

В конструктор `AgentHost(Config config)` — подписка ПОСЛЕ создания `_server`
(в конце конструктора):

```csharp
        // Зеркало диалогов: ошибка/копирование — событие сразу в сокет
        _dialogs.Updated += msg =>
        {
            if (_server.HasClients) _ = _server.BroadcastAsync(msg);
        };
```

В `StartAsync()` (рядом с созданием `_foreground` — хуки того же HookThread):

```csharp
        _dialogs.Start(); // отлов #32770: ошибки + прогресс копирования
```

В `Dispose()` (рядом с `_foreground?.Dispose()`):

```csharp
        _dialogs.Dispose();
```

Снапшот новому клиенту НЕ шлём: диалог — событие «сейчас», а не состояние;
опоздавший клиент старую ошибку видеть не должен.

## 6. `android/app/src/main/java/com/nofs/desk/ui/DeskScreen.kt`

Импорт (к остальным `com.nofs.desk.ui.components.*`):

```kotlin
import com.nofs.desk.ui.components.DialogMirrorCard
```

`mutableLongStateOf` уже импортирован (используется для `lastTouch`).

Локальный штамп «закрыто пользователем» — в `TabletDeskScreen`, рядом с
остальными remember-состояниями (после `var showSettings ...`):

```kotlin
    // Зеркало диалогов ПК: штамп последнего закрытого события —
    // карточка видна, пока пользователь не закрыл именно ЭТО событие
    var dialogDismissedAt by remember { mutableLongStateOf(0L) }
```

Сам оверлей — в правый нижний угол корневого Box. Ориентир: вставить МЕЖДУ
`SnackbarHost` и `Screensaver`. Сейчас это место выглядит так:

```kotlin
        // Ошибки от агента
        SnackbarHost(
            hostState = snackbarHost,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 66.dp)
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = DeskText,
                contentColor = DeskCard
            )
        }

        // Скринсейвер поверх всего
        Screensaver(
```

(чуть выше по файлу — блок `BottomPlayerPill` с
`.align(Alignment.BottomCenter).padding(bottom = 10.dp)`; наш оверлей
уходит в BottomEnd и с пилюлей не конфликтует).

Вставить после закрывающей скобки `SnackbarHost { ... }`:

```kotlin
        // Зеркало диалогов ПК: ошибка со скрином / прогресс копирования
        DialogMirrorCard(
            dialog = state.dialog,
            visible = state.dialog.at != 0L && state.dialog.at != dialogDismissedAt,
            onDismiss = { dialogDismissedAt = state.dialog.at },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp)
        )
```

Скринсейвер остаётся ПОСЛЕ оверлея (рисуется поверх — так и задумано:
спящий планшет карточкой не будим).

---

## Поведение и известные ограничения (проговорено в коде)

- **Win10/11 копирование**: диалог Explorer обычно DirectUIHWND без
  classic-бара `msctls_progress32` — тогда агент молчит (best-effort).
  Ловятся classic-диалоги и сторонние #32770-прогрессы.
- **PrintWindow**: сперва `PW_RENDERFULLCONTENT` (=2), фолбэк флаг 0;
  ускоренные окна могут отдать чёрный кадр — тогда на планшете хотя бы
  заголовок. `imageBase64` может быть null — карточка живёт без превью.
- **Размер**: скрин ужат до 800px, JPEG q70 (перевес 200 КБ → q50);
  на проводе разовое событие ~30–80 КБ base64.
- **Дедуп**: ошибка с того же hwnd — не чаще раза в 10 с; прогресс
  копирования шлётся только при смене процента (шаг 500 мс).
- **`kind="copy"` со `progressPct=100`** приходит и при отмене копирования
  (окно закрылось — отличить нельзя): планшет просто прячет плашку через 2 с.

## Чек после склейки

1. Агент собирается (`DialogMsg` в Protocol.cs — до этого
   DialogWatcherService.cs не компилируется).
2. Планшет собирается; в демо-режиме оверлея нет (at == 0).
3. Живой тест ошибки: на ПК
   `powershell -c "[System.Windows.Forms.MessageBox]::Show('Test error','Error')"`
   (через `Add-Type -AssemblyName System.Windows.Forms`) → карточка со скрином.
4. Тест «молчания»: обычный MessageBox без слов-маркеров — ничего не приходит.
