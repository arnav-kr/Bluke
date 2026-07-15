@echo off
REM Build the Bluke WiFi receiver for Windows using mingw-w64 gcc.
REM Produces a single self-contained bluke_receiver.exe (no runtime needed).

where gcc >nul 2>nul
if errorlevel 1 (
    echo [!] gcc not found. Install mingw-w64 ^(e.g. https://www.mingw-w64.org^) and add it to PATH.
    exit /b 1
)

gcc -O2 -Wall -o bluke_receiver.exe main.c -lws2_32 -luser32
if errorlevel 1 (
    echo [!] Build failed.
    exit /b 1
)

echo [+] Built bluke_receiver.exe
