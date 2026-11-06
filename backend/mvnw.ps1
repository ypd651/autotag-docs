$ideaMaven = "C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.3\plugins\maven-plugin\lib\maven3\bin\mvn.cmd"
if (Test-Path $ideaMaven) {
    & $ideaMaven $args
} else {
    mvn $args
}
