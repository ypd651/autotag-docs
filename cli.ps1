# ==============================================================================
# AutoTag Docs — CLI Headless Tool (Manejo sin Frontend)
# ==============================================================================
param (
    [Parameter(Position=0)]
    [string]$Action = "help",

    [Parameter(Position=1)]
    [string]$Param1 = "",

    [Parameter(Position=2)]
    [string]$Param2 = ""
)

$API = "http://localhost:8081/api"

function Show-Help {
    Write-Host "`n=== AutoTag Docs - Gestor CLI Headless ===" -ForegroundColor Cyan
    Write-Host "Uso:" -ForegroundColor Yellow
    Write-Host "  .\cli.ps1 upload <ruta-pdf>        -> Sube un archivo PDF y dispara el pipeline n8n"
    Write-Host "  .\cli.ps1 list                    -> Lista todos los documentos y sus estados"
    Write-Host "  .\cli.ps1 get <id-documento>       -> Muestra detalle, tags y resumen de un archivo"
    Write-Host "  .\cli.ps1 search <consulta>        -> Ejecuta una busqueda semantica vectorial"
    Write-Host "  .\cli.ps1 reprocess <id-documento> -> Fuerza a n8n a re-vectorizar el documento"
    Write-Host "  .\cli.ps1 health                  -> Diagnostico de Postgres, Qdrant y n8n`n"
}

switch ($Action.ToLower()) {
    "upload" {
        if (-not $Param1 -or -not (Test-Path $Param1)) {
            Write-Host "Error: Debes proporcionar la ruta a un archivo PDF valido." -ForegroundColor Red
            exit 1
        }
        Write-Host "Subiendo archivo '$Param1' a Spring Boot..." -ForegroundColor Cyan
        $res = & curl.exe -s -X POST "$API/documents/upload" -F "file=@$Param1"
        $json = $res | ConvertFrom-Json
        Write-Host "Documento creado en PostgreSQL!" -ForegroundColor Green
        Write-Host "ID:     $($json.id)"
        Write-Host "Estado: $($json.status)"
        Write-Host "Trigger despachado asincronamente hacia n8n." -ForegroundColor Yellow
    }

    "list" {
        $docs = Invoke-RestMethod -Uri "$API/documents" -Method Get
        Write-Host "`n--- Documentos Registrados en PostgreSQL ---" -ForegroundColor Cyan
        $docs | Select-Object id, fileName, status, chunksCount, tags | Format-Table -AutoSize
    }

    "get" {
        if (-not $Param1) {
            Write-Host "Error: Proporciona el UUID del documento." -ForegroundColor Red
            exit 1
        }
        $doc = Invoke-RestMethod -Uri "$API/documents/$Param1" -Method Get
        Write-Host "`n--- Detalle del Documento ---" -ForegroundColor Cyan
        Write-Host "ID:          $($doc.id)"
        Write-Host "Archivo:     $($doc.fileName) ($([math]::Round($doc.fileSize/1024, 1)) KB)"
        $color = if ($doc.status -eq "LISTO") { "Green" } else { "Yellow" }
        Write-Host "Estado:      $($doc.status)" -ForegroundColor $color
        Write-Host "Etiquetas:   $($doc.tags -join ', ')" -ForegroundColor Magenta
        Write-Host "Fragmentos:  $($doc.chunksCount) puntos en Qdrant"
        Write-Host "Resumen:     $($doc.summary)"
    }

    "search" {
        if (-not $Param1) {
            Write-Host "Error: Escribe una consulta semantica entre comillas." -ForegroundColor Red
            exit 1
        }
        Write-Host "Buscando por concepto vectorial en Qdrant: '$Param1'..." -ForegroundColor Cyan
        $results = Invoke-RestMethod -Uri "$API/search?q=$([uri]::EscapeDataString($Param1))&limit=5" -Method Get
        if ($results.Count -eq 0) {
            Write-Host "No se encontraron coincidencias semanticas." -ForegroundColor Yellow
        } else {
            foreach ($hit in $results) {
                Write-Host "`n------------------------------------------------------------" -ForegroundColor DarkGray
                Write-Host "Archivo:     $($hit.fileName)" -ForegroundColor Green
                Write-Host "Similitud:   $([math]::Round($hit.score * 100))% de coincidencia semantica" -ForegroundColor Yellow
                Write-Host "Tags:        $($hit.tags -join ', ')" -ForegroundColor Magenta
                Write-Host "Snippet:" -ForegroundColor White
                Write-Host "  `"$($hit.matchingSnippet)`"" -ForegroundColor Gray
            }
        }
    }

    "reprocess" {
        if (-not $Param1) {
            Write-Host "Error: Proporciona el UUID del documento." -ForegroundColor Red
            exit 1
        }
        $res = Invoke-RestMethod -Uri "$API/documents/$Param1/reprocess" -Method Post
        Write-Host "Pipeline re-disparado para el documento $($res.id). Estado: $($res.status)" -ForegroundColor Green
    }

    "health" {
        $diag = Invoke-RestMethod -Uri "$API/system/diagnostics" -Method Get
        Write-Host "`n--- Diagnostico de Servicios Headless ---" -ForegroundColor Cyan
        $pgColor = if ($diag.postgres.status -eq "UP") { "Green" } else { "Red" }
        $qdColor = if ($diag.qdrant.status -eq "UP") { "Green" } else { "Red" }
        $n8nColor = if ($diag.n8n_trigger.status -eq "UP") { "Green" } else { "Red" }
        Write-Host "PostgreSQL:   $($diag.postgres.status) ($($diag.postgres.detail))" -ForegroundColor $pgColor
        Write-Host "Qdrant DB:    $($diag.qdrant.status) (Coleccion: $($diag.qdrant.collection))" -ForegroundColor $qdColor
        Write-Host "n8n Trigger:  $($diag.n8n_trigger.status) ($($diag.n8n_trigger.webhook_url))" -ForegroundColor $n8nColor
    }

    default {
        Show-Help
    }
}
