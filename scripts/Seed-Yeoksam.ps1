param(
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$UserId = 'yeoksammock',
    [string]$Password = 'Yeoksam2026!'
)
$ErrorActionPreference = 'Stop'
$BaseUrl = $BaseUrl.TrimEnd('/')
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$csrf = Invoke-RestMethod "$BaseUrl/api/auth/csrf" -WebSession $session
$headers = @{}
$headers[$csrf.headerName] = $csrf.token
function Send-Json($Path, $Body) {
    $json = $Body | ConvertTo-Json -Depth 12
    Invoke-RestMethod "$BaseUrl$Path" -Method Post -WebSession $session -Headers $headers -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($json))
}
try {
    $null = Send-Json '/api/auth/login' @{userId=$UserId;password=$Password}
} catch {
    if ([int]$_.Exception.Response.StatusCode -ne 401) { throw }
    $null = Send-Json '/api/auth/signup' @{userId=$UserId;password=$Password;name='역삼 산책자';email="$UserId@example.com"}
    $null = Send-Json '/api/auth/login' @{userId=$UserId;password=$Password}
}
# Authentication can rotate the CSRF token.
$csrf = Invoke-RestMethod "$BaseUrl/api/auth/csrf" -WebSession $session
$headers = @{}
$headers[$csrf.headerName] = $csrf.token
$existing = Invoke-RestMethod "$BaseUrl/api/posts?authorId=$UserId&size=100" -WebSession $session
$titles = @($existing.items | ForEach-Object { $_.title })
$samples = Get-Content (Join-Path $PSScriptRoot 'yeoksam-posts.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$added = 0
foreach ($sample in $samples) {
    if ($titles -contains $sample.title) { continue }
    $post = Send-Json '/api/posts' $sample
    $titles += $post.title
    $added++
}
$saved = Invoke-RestMethod "$BaseUrl/api/posts?authorId=$UserId&size=100" -WebSession $session
Write-Output "Added: $added; posts in DB: $($saved.total)"

