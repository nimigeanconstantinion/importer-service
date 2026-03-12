for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format ddMMyyyyHHmm"') do (
    set VERSION=%%i
)

echo VERSION=%VERSION%
set SERVICE_VERSION=%VERSION%
