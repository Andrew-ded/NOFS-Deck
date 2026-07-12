using System.Diagnostics;
using System.Text;

namespace NofsAgent.Services;

/// <summary>
/// Локальный git через CLI: ветка, статус, ahead/behind, полный лог с графом,
/// ветки + checkout, операции pull / commit / push. Репозиторий можно менять
/// на лету (пункт «Отправить на планшет» в меню Проводника).
/// </summary>
public sealed class GitService(string initialRepoPath)
{
    /// <summary>Текущий путь к репозиторию (меняется через /set-repo).</summary>
    public string RepoPath { get; set; } = initialRepoPath;

    public bool IsConfigured =>
        !string.IsNullOrWhiteSpace(RepoPath) &&
        Directory.Exists(Path.Combine(RepoPath, ".git"));

    private string _lastSync = "";

    // ---------- снапшот ----------

    public async Task<GitMsg> SnapshotAsync(bool busy = false)
    {
        var repoName = string.IsNullOrWhiteSpace(RepoPath)
            ? ""
            : Path.GetFileName(RepoPath.TrimEnd('\\', '/'));

        if (!IsConfigured)
            return new GitMsg("нет репо", 0, 0, 0, false, "",
                new List<GitLogDto>(), new List<string>(), repoName, new List<string>());

        var branch = (await GitAsync("rev-parse --abbrev-ref HEAD")).Trim();
        if (string.IsNullOrEmpty(branch)) branch = "—";

        var statusLines = (await GitAsync("status --porcelain"))
            .Split('\n', StringSplitOptions.RemoveEmptyEntries);
        var dirty = statusLines.Length;
        var changes = statusLines.Take(30).Select(l => l.Trim()).ToList();

        int ahead = 0, behind = 0;
        var counts = (await GitAsync("rev-list --left-right --count @{upstream}...HEAD")).Trim();
        var parts = counts.Split('\t', ' ', StringSplitOptions.RemoveEmptyEntries);
        if (parts.Length == 2)
        {
            int.TryParse(parts[0], out behind);  // слева @{upstream} = behind
            int.TryParse(parts[1], out ahead);
        }

        var branches = (await GitAsync("branch --format=%(refname:short)"))
            .Split('\n', StringSplitOptions.RemoveEmptyEntries)
            .Select(b => b.Trim())
            .Where(b => b.Length > 0)
            .ToList();

        // Полная история всех веток с родителями (граф) и refs-метками
        var log = new List<GitLogDto>();
        var raw = await GitAsync(
            "log --all --topo-order -n 200 --pretty=format:%h%x1f%p%x1f%s%x1f%ct%x1f%D");
        foreach (var line in raw.Split('\n', StringSplitOptions.RemoveEmptyEntries))
        {
            var f = line.Split('\x1f');
            if (f.Length < 5 || !long.TryParse(f[3], out var ts)) continue;
            var parents = f[1].Split(' ', StringSplitOptions.RemoveEmptyEntries).ToList();
            log.Add(new GitLogDto(f[0], f[2], TimeAgoRu(ts), parents, f[4].Trim()));
        }

        return new GitMsg(branch, dirty, ahead, behind, busy, _lastSync,
            log, branches, repoName, changes);
    }

    // ---------- операции ----------

    public async Task<string?> PullAsync()
    {
        var err = await GitOpAsync("pull");
        // Ветка без трекинга — тянем явно из origin
        if (err != null && err.Contains("no tracking information", StringComparison.OrdinalIgnoreCase))
        {
            var branch = await CurrentBranchAsync();
            if (branch.Length > 0)
                err = await GitOpAsync($"pull origin \"{branch}\"");
        }
        if (err == null) MarkSync();
        return err;
    }

    public async Task<string?> CommitAsync(string message)
    {
        var err = await GitOpAsync("add -A");
        if (err != null) return err;
        // Сообщение через файл — без проблем с кавычками/юникодом
        var msgFile = Path.Combine(Path.GetTempPath(), $"nofs_commit_{Guid.NewGuid():N}.txt");
        await File.WriteAllTextAsync(msgFile, message, new UTF8Encoding(false));
        try
        {
            return await GitOpAsync($"commit -F \"{msgFile}\"");
        }
        finally
        {
            try { File.Delete(msgFile); } catch { }
        }
    }

    public async Task<string?> PushAsync()
    {
        var err = await GitOpAsync("push");
        // Первый пуш ветки — сами ставим upstream
        if (err != null && err.Contains("no upstream branch", StringComparison.OrdinalIgnoreCase))
        {
            var branch = await CurrentBranchAsync();
            if (branch.Length > 0)
                err = await GitOpAsync($"push --set-upstream origin \"{branch}\"");
        }
        if (err == null) MarkSync();
        return err;
    }

    private async Task<string> CurrentBranchAsync()
    {
        var branch = (await GitAsync("rev-parse --abbrev-ref HEAD")).Trim();
        return branch == "HEAD" ? "" : branch; // detached HEAD не трогаем
    }

    /// <summary>Переключение ветки. Имя проверяется по списку локальных веток.</summary>
    public async Task<string?> CheckoutAsync(string branch)
    {
        var known = (await GitAsync("branch --format=%(refname:short)"))
            .Split('\n', StringSplitOptions.RemoveEmptyEntries)
            .Select(b => b.Trim());
        if (!known.Contains(branch))
            return $"нет такой ветки: {branch}";
        return await GitOpAsync($"checkout \"{branch}\"");
    }

    private void MarkSync() => _lastSync = $"синк {DateTime.Now:HH:mm}";

    /// <summary>null = успех, строка = текст ошибки.</summary>
    private async Task<string?> GitOpAsync(string args)
    {
        var (code, _, stderr) = await RunGitAsync(args);
        if (code == 0) return null;
        var err = stderr.Trim();
        Log.Warn($"git {args}: {err}");
        return err.Length > 0 ? err : $"git {args} failed ({code})";
    }

    /// <summary>github-репо "owner/name" из remote origin (если в конфиге пусто).</summary>
    public async Task<string> DetectGitHubRepoAsync()
    {
        if (!IsConfigured) return "";
        var url = (await GitAsync("remote get-url origin")).Trim();
        // https://github.com/owner/name.git | git@github.com:owner/name.git
        var idx = url.IndexOf("github.com", StringComparison.OrdinalIgnoreCase);
        if (idx < 0) return "";
        var tail = url[(idx + "github.com".Length)..].TrimStart(':', '/');
        if (tail.EndsWith(".git")) tail = tail[..^4];
        return tail;
    }

    // ---------- запуск git ----------

    private async Task<string> GitAsync(string args) => (await RunGitAsync(args)).stdout;

    private async Task<(int code, string stdout, string stderr)> RunGitAsync(string args)
    {
        try
        {
            var psi = new ProcessStartInfo("git", args)
            {
                WorkingDirectory = RepoPath,
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                UseShellExecute = false,
                CreateNoWindow = true,
                StandardOutputEncoding = Encoding.UTF8,
                StandardErrorEncoding = Encoding.UTF8
            };
            using var p = Process.Start(psi)!;
            var stdout = await p.StandardOutput.ReadToEndAsync();
            var stderr = await p.StandardError.ReadToEndAsync();
            await p.WaitForExitAsync();
            return (p.ExitCode, stdout, stderr);
        }
        catch (Exception ex)
        {
            Log.Warn($"git {args}: {ex.Message}");
            return (-1, "", ex.Message);
        }
    }

    // ---------- «N назад» по-русски ----------

    public static string TimeAgoRu(long unixSeconds)
    {
        var then = DateTimeOffset.FromUnixTimeSeconds(unixSeconds).LocalDateTime;
        var span = DateTime.Now - then;
        return span switch
        {
            { TotalMinutes: < 1 } => "только что",
            { TotalMinutes: < 60 } => $"{(int)span.TotalMinutes} мин назад",
            { TotalHours: < 24 } => $"{(int)span.TotalHours} ч назад",
            { TotalDays: < 2 } => "вчера",
            { TotalDays: < 7 } => $"{(int)span.TotalDays} дн назад",
            _ => then.ToString("dd.MM.yyyy")
        };
    }
}
