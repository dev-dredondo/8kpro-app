$roots = @($env:ProgramFiles, "$env:LOCALAPPDATA\Programs", "$env:LOCALAPPDATA\JetBrains\Toolbox\apps") | Where-Object { $_ -and (Test-Path $_) }
foreach ($r in $roots) {
  $hit = Get-ChildItem $r -Recurse -Depth 6 -Filter java.exe -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -match '\\jbr\\bin\\java\.exe$' } | Select-Object -First 1
  if ($hit) { Split-Path (Split-Path $hit.FullName); break }
}
