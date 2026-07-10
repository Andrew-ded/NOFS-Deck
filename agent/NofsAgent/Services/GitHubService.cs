using System.Net.Http;
using System.Net.Http.Headers;
using System.Text.Json;

namespace NofsAgent.Services;

/// <summary>
/// Открытые PR и Issues через GitHub REST API.
/// Токен опционален (без него — только публичные репо, лимит 60 запр/час).
/// </summary>
public sealed class GitHubService(GitHubConfig config)
{
    private readonly HttpClient _http = CreateClient(config.Token);

    private static HttpClient CreateClient(string token)
    {
        var http = new HttpClient { Timeout = TimeSpan.FromSeconds(15) };
        http.DefaultRequestHeaders.UserAgent.Add(
            new ProductInfoHeaderValue("NofsAgent", "1.0"));
        http.DefaultRequestHeaders.Accept.Add(
            new MediaTypeWithQualityHeaderValue("application/vnd.github+json"));
        if (!string.IsNullOrWhiteSpace(token))
            http.DefaultRequestHeaders.Authorization =
                new AuthenticationHeaderValue("Bearer", token.Trim());
        return http;
    }

    /// <summary>repo — "owner/name". Ошибки не роняют агента: вернём пустые списки.</summary>
    public async Task<GitHubMsg> FetchAsync(string repo)
    {
        if (string.IsNullOrWhiteSpace(repo))
            return new GitHubMsg("", false, new(), new());

        var repoUrl = $"github.com/{repo}";
        var prs = new List<PullRequestDto>();
        var issues = new List<IssueDto>();

        try
        {
            // PR
            var prJson = await _http.GetStringAsync(
                $"https://api.github.com/repos/{repo}/pulls?state=open&per_page=10");
            using (var doc = JsonDocument.Parse(prJson))
            {
                foreach (var pr in doc.RootElement.EnumerateArray())
                {
                    prs.Add(new PullRequestDto(
                        Number: pr.GetProperty("number").GetInt32(),
                        Title: pr.GetProperty("title").GetString() ?? "",
                        Author: pr.GetProperty("user").GetProperty("login").GetString() ?? "",
                        Updated: TimeAgo(pr.GetProperty("updated_at").GetString())));
                }
            }

            // Issues (API отдаёт и PR — отфильтровать по ключу pull_request)
            var issJson = await _http.GetStringAsync(
                $"https://api.github.com/repos/{repo}/issues?state=open&per_page=20");
            using (var doc = JsonDocument.Parse(issJson))
            {
                foreach (var iss in doc.RootElement.EnumerateArray())
                {
                    if (iss.TryGetProperty("pull_request", out _)) continue;
                    var labels = new List<string>();
                    if (iss.TryGetProperty("labels", out var labelsEl))
                        foreach (var l in labelsEl.EnumerateArray())
                            if (l.GetProperty("name").GetString() is { } name)
                                labels.Add(name);

                    issues.Add(new IssueDto(
                        Number: iss.GetProperty("number").GetInt32(),
                        Title: iss.GetProperty("title").GetString() ?? "",
                        Labels: labels,
                        Updated: TimeAgo(iss.GetProperty("updated_at").GetString())));
                    if (issues.Count >= 10) break;
                }
            }
        }
        catch (Exception ex)
        {
            Log.Warn($"github {repo}: {ex.Message}");
        }

        return new GitHubMsg(repoUrl, false, prs, issues);
    }

    private static string TimeAgo(string? iso)
    {
        if (!DateTimeOffset.TryParse(iso, out var dt)) return "";
        return GitService.TimeAgoRu(dt.ToUnixTimeSeconds());
    }
}
