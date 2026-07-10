using Microsoft.Win32;

namespace NofsAgent;

/// <summary>
/// Пункт «Отправить на планшет (NOFS)» в контекстном меню Проводника
/// для папок (правый клик по папке и по фону открытой папки).
/// Пишется в HKCU — прав администратора не требует.
/// </summary>
public static class ExplorerMenu
{
    private const string Verb = "NofsDesk";
    private const string Caption = "Отправить на планшет (NOFS)";

    public static void Register()
    {
        var exe = Environment.ProcessPath ?? Application.ExecutablePath;
        try
        {
            // Правый клик по папке -> %1
            Write(@"Software\Classes\Directory\shell\" + Verb, exe, "%1");
            // Правый клик по фону открытой папки -> %V
            Write(@"Software\Classes\Directory\Background\shell\" + Verb, exe, "%V");
            Log.Info("explorer menu registered");
        }
        catch (Exception ex)
        {
            Log.Warn($"explorer menu: {ex.Message}");
        }
    }

    private static void Write(string keyPath, string exe, string arg)
    {
        using var key = Registry.CurrentUser.CreateSubKey(keyPath);
        key.SetValue("MUIVerb", Caption);
        key.SetValue("Icon", exe);
        using var cmd = key.CreateSubKey("command");
        cmd.SetValue("", $"\"{exe}\" --set-repo \"{arg}\"");
    }

    public static void Unregister()
    {
        try
        {
            Registry.CurrentUser.DeleteSubKeyTree(
                @"Software\Classes\Directory\shell\" + Verb, false);
            Registry.CurrentUser.DeleteSubKeyTree(
                @"Software\Classes\Directory\Background\shell\" + Verb, false);
        }
        catch (Exception ex)
        {
            Log.Warn($"explorer menu remove: {ex.Message}");
        }
    }
}
