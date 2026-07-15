using System.Text.RegularExpressions;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;
using Microsoft.CodeAnalysis.CSharp.Syntax;

namespace NofsAgent.Services;

/// <summary>
/// Разбор одного файла для «Паспорта файла»: что объявляет + от чего зависит.
/// Реализации: grep (эвристики), Roslyn (точный AST для C#), tree-sitter
/// (AST для C# и др.; Kotlin требует отдельного нативного tree-sitter-kotlin.dll).
/// «Используется в» считается отдельно грепом по репозиторию (см. FilePassportService).
/// </summary>
public interface IPassportAnalyzer
{
    /// <summary>Поддерживает ли расширение (".cs", ".kt", …).</summary>
    bool Supports(string ext);
    /// <summary>declares: "class X" / "method()"; deps: namespaces/imports. null — не смог.</summary>
    (List<string> declares, List<string> deps)? Analyze(string ext, string text);
}

// ---------- grep (эвристики) ----------

public sealed partial class GrepAnalyzer : IPassportAnalyzer
{
    public bool Supports(string ext) => true; // универсальный фолбэк

    public (List<string>, List<string>)? Analyze(string ext, string text)
    {
        var types = TypeDecl().Matches(text).Select(m => m.Groups[1].Value).Distinct().Take(6).ToList();
        var primary = types.FirstOrDefault();
        var methods = MethodCs().Matches(text).Select(m => m.Groups[1].Value)
            .Concat(MethodKt().Matches(text).Select(m => m.Groups[1].Value))
            .Where(n => n != primary).Distinct().Take(10).ToList();
        var declares = types.Select(t => $"class {t}").Concat(methods.Select(m => $"{m}()")).Take(12).ToList();
        var deps = UsingCs().Matches(text).Select(m => m.Groups[1].Value)
            .Concat(ImportOther().Matches(text).Select(m => m.Groups[1].Value))
            .Distinct().Take(10).ToList();
        return (declares, deps);
    }

    [GeneratedRegex(@"\b(?:class|interface|struct|enum|object)\s+(\w+)")] private static partial Regex TypeDecl();
    [GeneratedRegex(@"^\s*(?:public|private|protected|internal|static|async|override|virtual|\s)+[\w<>\[\],. ?]+\s+(\w+)\s*\([^;{)]*\)\s*(?:=>|\{|;)", RegexOptions.Multiline)] private static partial Regex MethodCs();
    [GeneratedRegex(@"\bfun\s+(\w+)\s*\(")] private static partial Regex MethodKt();
    [GeneratedRegex(@"^\s*using\s+([\w.]+)\s*;", RegexOptions.Multiline)] private static partial Regex UsingCs();
    [GeneratedRegex(@"^\s*import\s+([\w.*]+)", RegexOptions.Multiline)] private static partial Regex ImportOther();
}

// ---------- Roslyn (C#) ----------

public sealed class RoslynAnalyzer : IPassportAnalyzer
{
    public bool Supports(string ext) => ext.Equals(".cs", StringComparison.OrdinalIgnoreCase);

    public (List<string>, List<string>)? Analyze(string ext, string text)
    {
        if (!Supports(ext)) return null;
        try
        {
            var root = CSharpSyntaxTree.ParseText(text).GetCompilationUnitRoot();

            var types = root.DescendantNodes().OfType<BaseTypeDeclarationSyntax>()
                .Select(t => t.Identifier.Text).ToList();
            var primary = types.FirstOrDefault();

            var methods = root.DescendantNodes().OfType<MethodDeclarationSyntax>()
                .Select(m => m.Identifier.Text)
                .Concat(root.DescendantNodes().OfType<LocalFunctionStatementSyntax>()
                    .Select(m => m.Identifier.Text))
                .Where(n => n != primary)
                .Distinct().Take(12).ToList();

            var declares = types.Select(t => $"class {t}")
                .Concat(methods.Select(m => $"{m}()")).Take(14).ToList();

            var deps = root.Usings.Select(u => u.Name?.ToString() ?? "")
                .Where(s => s.Length > 0).Distinct().Take(12).ToList();

            return (declares, deps);
        }
        catch (Exception ex) { Log.Warn($"roslyn: {ex.Message}"); return null; }
    }
}

// ---------- tree-sitter (C# + опц. Kotlin) ----------

public sealed class TreeSitterAnalyzer : IPassportAnalyzer
{
    // расширение -> id языка для TreeSitter.Language(id): маппится в
    // "tree-sitter-<id>" + "tree_sitter_<id>" ("C#"→c-sharp). C#/Java/Scala
    // идут из пакета TreeSitter.DotNet; Kotlin требует tree-sitter-kotlin.dll
    // (win-x64) рядом с exe — грамматика fwcd/tree-sitter-kotlin.
    private static readonly Dictionary<string, string> Grammars = new(StringComparer.OrdinalIgnoreCase)
    {
        [".cs"] = "C#",
        [".java"] = "Java",
        [".scala"] = "Scala",
        [".kt"] = "Kotlin",
        [".kts"] = "Kotlin",
    };

    // tree-sitter query под объявления/импорты для C-подобных грамматик
    private const string CSharpDecls =
        "(class_declaration name: (identifier) @type)" +
        "(interface_declaration name: (identifier) @type)" +
        "(struct_declaration name: (identifier) @type)" +
        "(record_declaration name: (identifier) @type)" +
        "(enum_declaration name: (identifier) @type)" +
        "(method_declaration name: (identifier) @method)";
    private const string CSharpDeps = "(using_directive) @dep";

    private const string KotlinDecls =
        "(class_declaration (type_identifier) @type)" +
        "(object_declaration (type_identifier) @type)" +
        "(function_declaration (simple_identifier) @method)";
    private const string KotlinDeps = "(import_header) @dep";

    public bool Supports(string ext) => Grammars.ContainsKey(ext);

    public (List<string>, List<string>)? Analyze(string ext, string text)
    {
        if (!Grammars.TryGetValue(ext, out var langId)) return null;
        var isKotlin = ext is ".kt" or ".kts";
        try
        {
            using var language = new TreeSitter.Language(langId);
            using var parser = new TreeSitter.Parser(language);
            using var tree = parser.Parse(text);
            if (tree == null) return null;

            var declQuery = isKotlin ? KotlinDecls : CSharpDecls;
            var depQuery = isKotlin ? KotlinDeps : CSharpDeps;

            var types = new List<string>();
            var methods = new List<string>();
            using (var q = new TreeSitter.Query(language, declQuery))
                foreach (var cap in q.Execute(tree.RootNode).Captures)
                {
                    var name = cap.Node.Text;
                    if (cap.Name == "type") { if (!types.Contains(name)) types.Add(name); }
                    else if (!methods.Contains(name)) methods.Add(name);
                }

            var primary = types.FirstOrDefault();
            var declares = types.Take(6).Select(t => $"class {t}")
                .Concat(methods.Where(m => m != primary).Take(12).Select(m => $"{m}()"))
                .ToList();

            var deps = new List<string>();
            using (var q = new TreeSitter.Query(language, depQuery))
                foreach (var cap in q.Execute(tree.RootNode).Captures)
                {
                    var d = cap.Node.Text
                        .Replace("using", "").Replace("import", "")
                        .Replace(";", "").Trim();
                    if (d.Length > 0 && !deps.Contains(d)) deps.Add(d);
                }

            return (declares, deps.Take(12).ToList());
        }
        catch (Exception ex)
        {
            Log.Warn($"tree-sitter {ext}: {ex.Message}" +
                     (isKotlin ? " (нужен tree-sitter-kotlin.dll рядом с exe)" : ""));
            return null;
        }
    }
}

// ---------- выбор анализатора по конфигу ----------

public static class PassportAnalyzerFactory
{
    /// <summary>Цепочка анализаторов по режиму: пробуем по порядку, первый успех — берём.</summary>
    public static List<IPassportAnalyzer> Build(string mode)
    {
        var grep = new GrepAnalyzer();
        return mode.ToLowerInvariant() switch
        {
            "roslyn" => new List<IPassportAnalyzer> { new RoslynAnalyzer(), grep },
            "treesitter" => new List<IPassportAnalyzer> { new TreeSitterAnalyzer(), grep },
            "grep" => new List<IPassportAnalyzer> { grep },
            // auto: C# → Roslyn; Kotlin/Java/Scala → tree-sitter (если dll есть); иначе grep
            _ => new List<IPassportAnalyzer> { new RoslynAnalyzer(), new TreeSitterAnalyzer(), grep },
        };
    }
}
