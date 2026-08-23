@echo off
REM ─────────────────────────────────────────────────────────────────────────
REM  DocSummary AI — Backend Startup Script (Windows)
REM  Loads .env variables and starts the Spring Boot server
REM ─────────────────────────────────────────────────────────────────────────

IF NOT EXIST ".env" (
    echo [ERROR] .env file not found!
    echo Please create a .env file from .env.example and fill in your API key.
    pause
    exit /b 1
)

echo [INFO] Loading environment variables from .env...

REM Parse each line of .env (skip comments and blank lines)
FOR /F "usebackq tokens=1,* delims==" %%A IN (`findstr /v "^#" .env ^| findstr /v "^$"`) DO (
    SET "%%A=%%B"
    echo [INFO] Set %%A
)

echo.
echo [INFO] Starting DocSummary AI Backend on port %SERVER_PORT%...
echo [INFO] Gemini API Key: %GEMINI_API_KEY:~0,8%... (truncated for security)
echo.

mvn spring-boot:run -DskipTests

pause
