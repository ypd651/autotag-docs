@echo off
set "GRADLE_BIN=C:\Users\USER26\.gradle\wrapper\dists\gradle-9.7.1-bin\1w1c7tv4s851m17nbqdsro2tv\gradle-9.7.1\bin\gradle.bat"
if exist "%GRADLE_BIN%" (
    call "%GRADLE_BIN%" %*
) else (
    gradle %*
)
