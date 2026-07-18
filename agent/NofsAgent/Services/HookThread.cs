using System.Runtime.InteropServices;
using System.Windows.Forms;

namespace NofsAgent.Services;

/// <summary>
/// Один выделенный поток на ВСЕ WinEvent-хуки агента.
/// SetWinEventHook (OUTOFCONTEXT) доставляет события в тот поток, который
/// поставил хук, и только если тот крутит message loop — поэтому держим один
/// STA-поток с Application.Run и ставим/снимаем хуки строго С НЕГО
/// (через Control.Invoke на скрытый контрол).
/// Поток фоновый и живёт до конца процесса; своего Dispose у HookThread нет —
/// каждая подписка снимает свой хук через возвращённый IDisposable.
/// Потребители: ForegroundWatcher (смена фокуса); дальше сюда же лягут
/// отлов диалогов ошибок (#32770) и зеркало прогресс-баров Explorer.
/// </summary>
public sealed class HookThread
{
    private static readonly Lazy<HookThread> Lazy = new(() => new HookThread());
    public static HookThread Instance => Lazy.Value;

    // Скрытый контрол с готовым хэндлом — мост «любой поток → hook-поток».
    private Control _invoker = null!; // заполняется до выхода из конструктора (ready)

    private HookThread()
    {
        var ready = new ManualResetEventSlim();
        var thread = new Thread(() =>
        {
            _invoker = new Control();
            _ = _invoker.Handle; // форсируем создание окна — Invoke работает с первой секунды
            ready.Set();
            Application.Run(new ApplicationContext()); // message loop: сюда прилетают WinEvents
        })
        { IsBackground = true, Name = "nofs-winevent-hooks" };
        thread.SetApartmentState(ApartmentState.STA);
        thread.Start();
        ready.Wait();
    }

    /// <summary>
    /// Повесить WinEvent-хук на диапазон событий [eventMin..eventMax].
    /// Колбэк отдаёт hwnd окна-источника и зовётся НА hook-потоке —
    /// тяжёлую работу потребитель уводит в Task.Run/таймер у себя.
    /// Dispose возвращённого объекта снимает хук.
    /// </summary>
    public IDisposable Register(uint eventMin, uint eventMax, Action<IntPtr> onEvent)
        => new Registration(_invoker, eventMin, eventMax, onEvent);

    // ---------- P/Invoke ----------

    private delegate void WinEventDelegate(
        IntPtr hWinEventHook, uint eventType, IntPtr hwnd,
        int idObject, int idChild, uint dwEventThread, uint dwmsEventTime);

    [DllImport("user32.dll")]
    private static extern IntPtr SetWinEventHook(
        uint eventMin, uint eventMax, IntPtr hmodWinEventProc,
        WinEventDelegate lpfnWinEventProc, uint idProcess, uint idThread, uint dwFlags);

    [DllImport("user32.dll")]
    private static extern bool UnhookWinEvent(IntPtr hWinEventHook);

    private const uint WINEVENT_OUTOFCONTEXT = 0x0000;   // без DLL-инжекта, события через очередь
    private const uint WINEVENT_SKIPOWNPROCESS = 0x0002; // свои окна (трей) не отражаем

    /// <summary>Одна подписка = один хук; Dispose снимает его (тоже с hook-потока).</summary>
    private sealed class Registration : IDisposable
    {
        private readonly Control _invoker;
        // КРИТИЧНО: делегат живёт в поле всё время жизни хука. Отдать в
        // SetWinEventHook «голую» лямбду нельзя — GC соберёт делегат, и Windows
        // позовёт мёртвый указатель: нативный краш без managed-стека.
        private readonly WinEventDelegate _proc;
        private IntPtr _hook;

        public Registration(Control invoker, uint eventMin, uint eventMax, Action<IntPtr> onEvent)
        {
            _invoker = invoker;
            _proc = (_, evt, hwnd, _, _, _, _) =>
            {
                // Исключение потребителя не должно ронять message loop хуков
                try { onEvent(hwnd); }
                catch (Exception ex) { Log.Warn($"winevent 0x{evt:X}: {ex.Message}"); }
            };
            _invoker.Invoke(() =>
            {
                _hook = SetWinEventHook(eventMin, eventMax, IntPtr.Zero, _proc,
                    0, 0, WINEVENT_OUTOFCONTEXT | WINEVENT_SKIPOWNPROCESS);
            });
            if (_hook == IntPtr.Zero)
                Log.Warn($"SetWinEventHook 0x{eventMin:X}..0x{eventMax:X} не встал");
        }

        public void Dispose()
        {
            var h = Interlocked.Exchange(ref _hook, IntPtr.Zero);
            if (h == IntPtr.Zero) return; // хук не встал или уже снят
            try { _invoker.Invoke(() => UnhookWinEvent(h)); }
            catch (Exception ex) { Log.Warn($"UnhookWinEvent: {ex.Message}"); }
        }
    }
}
