namespace NofsAgent.Services;

/// <summary>
/// Смена активного окна — событием, а не поллингом: EVENT_SYSTEM_FOREGROUND
/// через общий HookThread. Базовый примитив всех «зеркальных» фич.
/// Alt-Tab и переключение через панель задач генерят очередь событий подряд,
/// поэтому дебаунс 100 мс: Changed стреляет один раз, когда пыль осела.
/// Changed зовётся с пула потоков (таймер) — подписчик сам решает, что дальше.
/// </summary>
public sealed class ForegroundWatcher : IDisposable
{
    private const uint EVENT_SYSTEM_FOREGROUND = 0x0003;
    private const int DebounceMs = 100;

    private readonly IDisposable _hook;
    private readonly System.Threading.Timer _debounce; // полное имя: WinForms тоже даёт Timer

    /// <summary>Фокус перешёл к другому окну (после дебаунса).</summary>
    public event Action? Changed;

    public ForegroundWatcher()
    {
        _debounce = new System.Threading.Timer(
            _ => Changed?.Invoke(), null, Timeout.Infinite, Timeout.Infinite);
        // Каждое событие перезаводит таймер: сработает через 100 мс после последнего
        _hook = HookThread.Instance.Register(EVENT_SYSTEM_FOREGROUND, EVENT_SYSTEM_FOREGROUND,
            _ => _debounce.Change(DebounceMs, Timeout.Infinite));
    }

    public void Dispose()
    {
        _hook.Dispose();     // сперва хук — чтобы никто не перезавёл таймер
        _debounce.Dispose();
    }
}
