@echo off
call service_version_number.bat

if not defined SERVICE_VERSION (
    echo Error: SERVICE_VERSION is not defined in service_version_number.bat.
    exit /b 1
)

echo Building Docker image...
docker build -f Dockerfile -t importer-service:%SERVICE_VERSION% .

echo Getting the new image ID...
setlocal enabledelayedexpansion
for /f "tokens=*" %%i in ('docker images -q importer-service:%SERVICE_VERSION%') do set IMAGE_ID=%%i
echo New image ID: !IMAGE_ID!
endlocal

echo Tagging the image...
docker tag importer-service:%SERVICE_VERSION%  ion21/importer-service:%SERVICE_VERSION%

echo Pushing the tagged image...
docker push  ion21/importer-service:%SERVICE_VERSION%

echo Script completed.
pause
