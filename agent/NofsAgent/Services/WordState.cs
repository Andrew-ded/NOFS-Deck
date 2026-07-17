using System.Runtime.InteropServices;

namespace NofsAgent.Services;

/// <summary>
/// Отражение состояния форматирования в Word через COM-автоматизацию:
/// Selection.Font.Bold/Italic/Underline. Кнопка «Жирный» на планшете
/// подсвечивается, когда курсор реально в жирном тексте, и гаснет сама —
/// тот самый «живой тумблер». Берём запущенный Word из Running Object Table
/// (GetActiveObject) и читаем через dynamic (IDispatch).
/// </summary>
public sealed class WordState
{
    [DllImport("oleaut32.dll", ExactSpelling = true, PreserveSig = false)]
    private static extern void GetActiveObject(
        ref Guid rclsid, IntPtr pvReserved,
        [MarshalAs(UnmanagedType.Interface)] out object ppunk);

    [DllImport("ole32.dll", ExactSpelling = true, PreserveSig = false)]
    private static extern void CLSIDFromProgID(
        [MarshalAs(UnmanagedType.LPWStr)] string progId, out Guid clsid);

    private dynamic? _word;

    /// <summary>what: "bold" | "italic" | "underline". null — Word не запущен/ошибка.</summary>
    public bool? Eval(string what)
    {
        try
        {
            var word = GetWord();
            if (word == null) return false;

            var font = word.Selection.Font;
            // Bold/Italic: 0 = нет, -1 = всё, wdUndefined(9999999) = смешанно → считаем «есть».
            // Underline: 0 = нет, иначе тип подчёркивания.
            int v = what switch
            {
                "bold" => (int)font.Bold,
                "italic" => (int)font.Italic,
                "underline" => (int)font.Underline,
                _ => 0,
            };
            return v != 0;
        }
        catch
        {
            _word = null; // Word закрыли/занят — сбросим кэш, попробуем в следующий раз
            return false;
        }
    }

    private dynamic? GetWord()
    {
        // Проверяем кэш живой ли
        if (_word != null)
        {
            try { _ = _word.Selection; return _word; }
            catch { _word = null; }
        }
        try
        {
            CLSIDFromProgID("Word.Application", out var clsid);
            GetActiveObject(ref clsid, IntPtr.Zero, out var obj);
            _word = obj;
            return _word;
        }
        catch { return null; } // Word не запущен — MK_E_UNAVAILABLE
    }
}
