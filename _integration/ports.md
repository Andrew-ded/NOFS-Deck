# Интеграция: мониторинг портов (п.8 бэклога)

Готовые файлы (не трогать, уже лежат):
- `agent/NofsAgent/Services/PortWatcherService.cs` — сервис агента (GetExtendedTcpTable,
  тик 3 с, пуш только при смене сигнатуры порт+pid, `Open(port)` — открыть в браузере ПК).
- `android/.../ui/components/PortsCard.kt` — карточка с чипами `:5000 · dotnet`.

Ниже — точные блоки для вставки. Протокол — зеркала, менять строго синхронно.

---

## 1. `agent/NofsAgent/Protocol.cs`

В секцию `// ---------- агент -> планшет ----------`, после `ClaudeUsageMsg`
(перед `// ---------- планшет -> агент ----------`). Сигнатуры РОВНО эти —
их использует PortWatcherService.cs (`new PortDto(port, pid, name)`,
`new PortsMsg(entries)`, чтение `e.Port` / `e.Pid`):

```csharp
/// <summary>Слушающий TCP-порт: кто сидит на порту (dotnet на 5000, vite на 5173).</summary>
public sealed record PortDto(int Port, int Pid, string Process);

/// <summary>Снимок слушающих портов; пуш только при смене набора порт+pid.</summary>
public sealed record PortsMsg(List<PortDto> Ports)
{
    public string Type => "ports";
}
```

JSON на проводе (camelCase): `{"ports":[{"port":5000,"pid":1234,"process":"dotnet"}],"type":"ports"}`.

---

## 2. `android/app/src/main/java/com/nofs/desk/net/Protocol.kt`

Зеркальные модели — после `ClaudeUsageMsg` (перед `// ---------- планшет -> агент ----------`):

```kotlin
/** Слушающий TCP-порт на ПК: кто на каком порту сидит. Зеркало PortDto/PortsMsg. */
@Serializable
data class PortDto(
    val port: Int,
    val pid: Int = 0,
    val process: String = ""
)

@Serializable
data class PortsMsg(val ports: List<PortDto> = emptyList())
```

В `object Cmd` — после `claudeCalibrate`:

```kotlin
/** Тап по чипу порта: открыть http://localhost:порт в браузере ПК. */
fun openPort(port: Int): JsonObject = buildJsonObject {
    put("type", "cmd"); put("cmd", "openPort"); put("value", port.toFloat())
}
```

(Порт едет в `value: Float` — единственное числовое поле CmdMsg; агент
приводит обратно `(int)(cmd.Value ?? 0)`.)

---

## 3. `android/app/src/main/java/com/nofs/desk/data/DeskState.kt`

Модель — рядом с `ClaudeUsage` (перед `data class DeskState`):

```kotlin
/** Слушающий порт ПК — чип на карточке «Порты». */
data class PortEntry(
    val port: Int,
    val pid: Int = 0,
    val process: String = ""
)
```

Поле в `data class DeskState` — после `val claude: ClaudeUsage = ClaudeUsage()`:

```kotlin
    val ports: List<PortEntry> = emptyList()
```

(Не забыть запятую после строки `claude`.)

Команда в `sealed interface DeskCommand` — после `ClaudeCalibrate`:

```kotlin
    /** Тап по чипу порта: открыть localhost:порт в браузере ПК. */
    data class OpenPort(val port: Int) : DeskCommand
```

---

## 4. `android/app/src/main/java/com/nofs/desk/net/WebSocketDeskDataSource.kt`

Импорт (в блок импортов `com.nofs.desk.data.*`, по алфавиту рядом с `PlaytimeEntry`):

```kotlin
import com.nofs.desk.data.PortEntry
```

Ветка в `handleMessage` `when (peekType(obj))` — рядом с `"claude"`:

```kotlin
            "ports" -> {
                val p = ProtocolJson.decodeFromJsonElement<PortsMsg>(obj)
                _state.update { st ->
                    st.copy(ports = p.ports.map { PortEntry(it.port, it.pid, it.process) })
                }
            }
```

Ветка в `send` `when (command)` — после `is DeskCommand.ClaudeCalibrate -> ...`:

```kotlin
            is DeskCommand.OpenPort -> Cmd.openPort(command.port)
```

(`when` по sealed-интерфейсу exhaustive — без этой ветки не соберётся,
компилятор сам напомнит.)

---

## 5. `android/app/src/main/java/com/nofs/desk/data/FakeDeskDataSource.kt`

Демо-порты — в конструктор начального `DeskState` (в `_state = MutableStateFlow(DeskState(...))`),
после блока `claude = ClaudeUsage(...)`:

```kotlin
            ports = listOf(
                PortEntry(5000, 1234, "dotnet"),
                PortEntry(5173, 5678, "node"),
                PortEntry(48484, 9012, "NofsAgent")
            )
```

(Запятая после закрывающей скобки `claude = ClaudeUsage(...)`.)

Ветка в `send` `when (command)` — рядом с `ClaudeCalibrate`:

```kotlin
            is DeskCommand.OpenPort -> { /* демо: на ПК нечего открывать */ }
```

---

## 6. `agent/NofsAgent/AgentHost.cs`

Поле — рядом с `_claude`:

```csharp
    private readonly PortWatcherService _ports = new();
```

Конструктор — после `_discovery = new DiscoveryResponder(...)` (нужен готовый `_server`):

```csharp
        // Порты: пуш события при смене набора слушателей (сервис сам фильтрует шум)
        _ports.Updated += msg => _ = Task.Run(async () =>
        {
            try
            {
                if (!_server.HasClients) return;
                await _server.BroadcastAsync(msg);
            }
            catch (Exception ex) { Log.Warn($"ports push: {ex.Message}"); }
        });
```

`StartAsync` — рядом с запуском циклов (после `_ = LoopAsync(60_000, PushClaudeAsync);`):

```csharp
        _ports.Start();                           // порты: свой цикл 3 с внутри сервиса
```

`SendSnapshotAsync` — в конец, после снапшота `_claude`:

```csharp
        await _server.SendAsync(clientId, _ports.Current);
```

`HandleCommandAsync` — в `switch (cmd.Cmd)`, перед `default`:

```csharp
            case "openPort": _ports.Open((int)(cmd.Value ?? 0)); break;
```

`Dispose` — рядом с `_audio.Dispose();`:

```csharp
        _ports.Dispose();
```

---

## 7. `android/app/src/main/java/com/nofs/desk/ui/DeskScreen.kt`

Импорт (блок `com.nofs.desk.ui.components.*`, по алфавиту после `PlayerSheet`):

```kotlin
import com.nofs.desk.ui.components.PortsCard
```

Вставка — сразу после блока ClaudeUsageCard. Сейчас там (строки ~224–234):

```kotlin
                    // Лимиты Claude (ccusage на ПК); в демо-режиме — образец
                    if (state.claude.ok) {
                        ClaudeUsageCard(
                            usage = state.claude,
                            onCalibrate = { scope, pct ->
                                viewModel.send(DeskCommand.ClaudeCalibrate(scope, pct))
                            }
                        )
                        Spacer(Modifier.height(14.dp))
                    }

                    MacroPanel(
```

Между `}` (конец if claude) и `MacroPanel(` вставить:

```kotlin
                    // Порты ПК: кто на каком порту слушает; тап — открыть на ПК.
                    // Пустой список карточка прячет сама, обёртка — только для Spacer.
                    if (state.ports.isNotEmpty()) {
                        PortsCard(
                            ports = state.ports,
                            onOpen = { viewModel.send(DeskCommand.OpenPort(it)) }
                        )
                        Spacer(Modifier.height(14.dp))
                    }
```

---

## Проверка после сшивки
1. `dotnet build` агента — PortsMsg/PortDto резолвятся в PortWatcherService.cs.
2. Сборка Android — exhaustive `when` в обоих источниках данных доволен.
3. Демо-режим: карточка «Порты» с тремя чипами под карточкой Claude.
4. Живой ПК: чипы совпадают с `netstat -ano | findstr LISTENING` (минус системный шум
   и порты < 1024); тап по чипу открывает браузер на ПК.
