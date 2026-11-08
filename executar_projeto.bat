@echo off
cd /d "%~dp0"
chcp 65001 >nul
title Projeto C.A.S.A. - Executar Projeto

echo =================================================================
echo        PROJETO C.A.S.A. - INICIALIZAÇÃO AUTOMÁTICA
echo =================================================================
echo.

:: 1. Se o Python estiver instalado, executa via Python (suporte completo e scrcpy)
where python >nul 2>nul
if %ERRORLEVEL% equ 0 (
    echo [INFO] Python detectado. Executando assistente inteligente...
    python "%~dp0setup_e_executar.py"
    if errorlevel 1 (
        echo.
        echo =================================================================
        echo   A EXECUÇÃO FOI ENCERRADA COM ERRO. VERIFIQUE AS MENSAGENS ACIMA.
        echo =================================================================
        echo.
        pause
        exit /b 1
    ) else (
        goto :fim_sucesso
    )
)

:: 2. Modo Nativo (.BAT puro sem necessidade de Python)
echo [1/5] Detectando Android SDK...

set "SDK_DIR="

if exist "local.properties" (
    for /f "tokens=1,* delims==" %%A in (local.properties) do (
        if "%%A"=="sdk.dir" (
            set "SDK_DIR=%%B"
        )
    )
)

if not exist "%SDK_DIR%" (
    if exist "%LOCALAPPDATA%\Android\Sdk" (
        set "SDK_DIR=%LOCALAPPDATA%\Android\Sdk"
    ) else if exist "%USERPROFILE%\AppData\Local\Android\Sdk" (
        set "SDK_DIR=%USERPROFILE%\AppData\Local\Android\Sdk"
    ) else if exist "C:\Android\Sdk" (
        set "SDK_DIR=C:\Android\Sdk"
    ) else if defined ANDROID_HOME (
        set "SDK_DIR=%ANDROID_HOME%"
    )
)

if not exist "%SDK_DIR%" (
    echo [ERRO] Android SDK não encontrado automaticamente!
    echo        Abra o Android Studio ou defina a variável ANDROID_HOME.
    goto :erro
)

echo [OK] Android SDK localizado: %SDK_DIR%

set "ESCAPED_SDK=%SDK_DIR:\=\\%"
set "ESCAPED_SDK=%ESCAPED_SDK::=\:%"
echo # Gerado automaticamente pelo executar_projeto.bat > local.properties
echo sdk.dir=%ESCAPED_SDK% >> local.properties
echo [OK] local.properties configurado.

:: 3. Detectando Java JDK compatível
echo.
echo [2/5] Detectando Java JDK compatível (Java 17 a 21 ou Android Studio JBR)...

set "JAVA_TARGET="

:: 3.1. Verifica JDK portátil previamente baixado em .jdk/
for /d %%D in ("%~dp0.jdk\jdk*") do (
    if exist "%%D\bin\javac.exe" set "JAVA_TARGET=%%D"
)
if not defined JAVA_TARGET (
    if exist "%~dp0.jdk\bin\javac.exe" set "JAVA_TARGET=%~dp0.jdk"
)

:: 3.2. Verifica o JBR do Android Studio no disco (funciona com Android Studio fechado!)
if not defined JAVA_TARGET (
    if exist "%PROGRAMFILES%\Android\Android Studio\jbr\bin\javac.exe" (
        set "JAVA_TARGET=%PROGRAMFILES%\Android\Android Studio\jbr"
    ) else if exist "%LOCALAPPDATA%\Programs\Android Studio\jbr\bin\javac.exe" (
        set "JAVA_TARGET=%LOCALAPPDATA%\Programs\Android Studio\jbr"
    ) else if exist "D:\Program Files\Android\Android Studio\jbr\bin\javac.exe" (
        set "JAVA_TARGET=D:\Program Files\Android\Android Studio\jbr"
    )
)

:: 3.3. Verifica JDKs 17 a 21 instalados
if not defined JAVA_TARGET (
    if exist "C:\Program Files\Java\jdk-21.0.11" (
        set "JAVA_TARGET=C:\Program Files\Java\jdk-21.0.11"
    ) else if exist "C:\Program Files\Java\jdk-21" (
        set "JAVA_TARGET=C:\Program Files\Java\jdk-21"
    ) else if exist "C:\Program Files\Java\jdk-17" (
        set "JAVA_TARGET=C:\Program Files\Java\jdk-17"
    ) else if defined JAVA_HOME (
        echo %JAVA_HOME% | findstr /i "24 25" >nul
        if errorlevel 1 (
            set "JAVA_TARGET=%JAVA_HOME%"
        ) else (
            echo [AVISO] JAVA_HOME aponta para versão incompatível (Java 24/25). Ignorando...
        )
    )
)

:: 3.4. Se ainda não encontrou, baixa JDK 21 portátil automaticamente
if not defined JAVA_TARGET (
    echo [INFO] Baixando JDK 21 portátil automaticamente (apenas na 1ª vez)...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $dest='%~dp0.jdk'; New-Item -ItemType Directory -Force -Path $dest | Out-Null; $zip=Join-Path $dest 'temp_jdk.zip'; [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk' -OutFile $zip; Expand-Archive -Path $zip -DestinationPath $dest -Force; Remove-Item $zip -Force;"
    for /d %%D in ("%~dp0.jdk\jdk*") do (
        if exist "%%D\bin\javac.exe" set "JAVA_TARGET=%%D"
    )
)

if defined JAVA_TARGET (
    echo [OK] JDK selecionado: %JAVA_TARGET%
    set "JAVA_HOME=%JAVA_TARGET%"
    set "PATH=%JAVA_TARGET%\bin;%PATH%"
    set "GRADLE_JVM_ARG=-Dorg.gradle.java.home=%JAVA_TARGET:\=/%"
) else (
    echo [AVISO] JDK específico não encontrado. Usando Java padrão do sistema.
    set "GRADLE_JVM_ARG="
)

:: 4. Baixar dependências e compilar APK
echo.
echo [3/5] Baixando dependências e compilando app (assembleDebug)...
call gradlew.bat %GRADLE_JVM_ARG% assembleDebug
if %ERRORLEVEL% neq 0 (
    echo [ERRO] Falha ao compilar com Gradle.
    goto :erro
)
echo [OK] Compilação finalizada com sucesso!

:: 5. Verificar ADB e dispositivos
echo.
echo [4/5] Verificando conexão com celular ou emulador...
set "ADB_EXE=%SDK_DIR%\platform-tools\adb.exe"

if not exist "%ADB_EXE%" (
    echo [AVISO] adb.exe não encontrado em %SDK_DIR%\platform-tools.
    echo        O APK foi gerado em: app\build\outputs\apk\debug\app-debug.apk
    goto :fim_sucesso
)

"%ADB_EXE%" devices > "%TEMP%\adb_devices.txt"
set "DEVICE_SERIAL="
set "IS_EMULATOR=0"

for /f "skip=1 tokens=1,2" %%A in ('type "%TEMP%\adb_devices.txt"') do (
    if "%%B"=="device" (
        set "DEVICE_SERIAL=%%A"
        echo %%A | findstr /i "emulator" >nul
        if %ERRORLEVEL% equ 0 (
            set "IS_EMULATOR=1"
        )
        goto :dispositivo_encontrado
    )
)

:dispositivo_encontrado
if not defined DEVICE_SERIAL (
    echo [AVISO] Nenhum celular conectado ou emulador ativo detectado.
    echo        Conecte seu celular via USB com 'Depuração USB' ativada,
    echo        ou abra o emulador no Android Studio.
    echo.
    echo        O APK está pronto em: app\build\outputs\apk\debug\app-debug.apk
    goto :fim_sucesso
)

echo [OK] Dispositivo conectado: %DEVICE_SERIAL%

:: 6. Instalar e rodar
echo.
echo [5/5] Instalando e iniciando o app no dispositivo...
set "APK_FILE=app\build\outputs\apk\debug\app-debug.apk"
"%ADB_EXE%" -s %DEVICE_SERIAL% install -r "%APK_FILE%"
if %ERRORLEVEL% equ 0 (
    echo [OK] APK instalado com sucesso!
    echo Iniciando MainActivity...
    "%ADB_EXE%" -s %DEVICE_SERIAL% shell am start -n fatec.labweb.projetocasa/.view.MainActivity
    echo [OK] Aplicativo aberto no celular/emulador!

    :: Se for celular USB fisico, inicia transmissao de tela via scrcpy
    if "%IS_EMULATOR%"=="0" (
        echo.
        echo [scrcpy] Conexão USB física detectada! Verificando scrcpy para espelhamento de tela...
        set "SCRCPY_EXE="

        where scrcpy >nul 2>nul
        if %ERRORLEVEL% equ 0 (
            set "SCRCPY_EXE=scrcpy"
        )

        if not defined SCRCPY_EXE (
            for /d %%D in (scrcpy*) do (
                if exist "%%D\scrcpy.exe" set "SCRCPY_EXE=%%D\scrcpy.exe"
            )
        )

        if not defined SCRCPY_EXE (
            echo [scrcpy] Baixando scrcpy portátil automaticamente...
            curl -L -o "scrcpy-temp.zip" "https://github.com/genymobile/scrcpy/releases/download/v5.0.1/scrcpy-win64-v5.0.1.zip"
            if exist "scrcpy-temp.zip" (
                tar -xf "scrcpy-temp.zip"
                del "scrcpy-temp.zip"
                for /d %%D in (scrcpy*) do (
                    if exist "%%D\scrcpy.exe" set "SCRCPY_EXE=%%D\scrcpy.exe"
                )
            )
        )

        if defined SCRCPY_EXE (
            echo [scrcpy] Abrindo transmissão de tela...
            start "" "%SCRCPY_EXE%" -s %DEVICE_SERIAL% --window-title="C.A.S.A. Mobile - Tela do Celular (USB)"
            echo [OK] Janela do scrcpy iniciada!
        ) else (
            echo [AVISO] scrcpy não pôde ser baixado automaticamente. Baixe em https://github.com/genymobile/scrcpy/releases
        )
    )
) else (
    echo [AVISO] Não foi possível instalar automaticamente pelo ADB.
)

:fim_sucesso
echo.
echo =================================================================
echo   TUDO PRONTO! PROJETO SINCRONIZADO E EXECUTADO COM SUCESSO.
echo =================================================================
echo.
pause
exit /b 0

:erro
echo.
echo =================================================================
echo   OCORREU UM ERRO DURANTE A EXECUÇÃO. VERIFIQUE AS MENSAGENS ACIMA.
echo =================================================================
echo.
pause
exit /b 1
