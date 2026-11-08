#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Script de Inicialização e Execução Automática - C.A.S.A. Mobile
Detecta SDK, JDK, configura local.properties, baixa dependências, compila, instala,
abre o app e transmite a tela via scrcpy caso conectado via USB.
"""

import os
import sys
import subprocess
import time
import shutil
import zipfile
import urllib.request
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent
APP_PACKAGE = "fatec.labweb.projetocasa"
MAIN_ACTIVITY = ".view.MainActivity"

SCRCPY_DOWNLOAD_URL = "https://github.com/genymobile/scrcpy/releases/download/v5.0.1/scrcpy-win64-v5.0.1.zip"

def print_header(title):
    print("\n" + "=" * 65)
    print(f"  {title}")
    print("=" * 65)

def print_step(step, msg):
    print(f"\n[+] ({step}) {msg}")

def print_ok(msg):
    print(f"    [OK] {msg}")

def print_warn(msg):
    print(f"    [AVISO] {msg}")

def print_error(msg):
    print(f"    [ERRO] {msg}")

def find_android_sdk():
    """Detecta a localização do Android SDK no sistema."""
    local_props = PROJECT_ROOT / "local.properties"
    if local_props.exists():
        try:
            with open(local_props, "r", encoding="utf-8") as f:
                for line in f:
                    if line.strip().startswith("sdk.dir"):
                        path_str = line.split("=", 1)[1].strip().replace("\\:", ":").replace("\\\\", "\\")
                        sdk_path = Path(path_str)
                        if sdk_path.exists():
                            return sdk_path
        except Exception:
            pass

    for env_var in ["ANDROID_HOME", "ANDROID_SDK_ROOT"]:
        val = os.environ.get(env_var)
        if val and Path(val).exists():
            return Path(val)

    possible_paths = [
        Path(os.environ.get("LOCALAPPDATA", "")) / "Android" / "Sdk",
        Path(os.environ.get("USERPROFILE", "")) / "AppData" / "Local" / "Android" / "Sdk",
        Path("C:/Android/Sdk"),
        Path("C:/Program Files (x86)/Android/android-sdk")
    ]
    for p in possible_paths:
        if p.exists():
            return p

    return None

def find_jdk():
    """Localiza uma versão compatível de JDK (17 a 21) ou o JBR do Android Studio."""
    java_home = os.environ.get("JAVA_HOME")
    if java_home and Path(java_home).exists():
        return Path(java_home)

    studio_jbr_paths = [
        Path(os.environ.get("PROGRAMFILES", "C:/Program Files")) / "Android" / "Android Studio" / "jbr",
        Path(os.environ.get("LOCALAPPDATA", "")) / "Programs" / "Android Studio" / "jbr"
    ]
    for p in studio_jbr_paths:
        if p.exists() and (p / "bin" / "javac.exe").exists():
            return p

    java_dir = Path("C:/Program Files/Java")
    if java_dir.exists():
        for folder in ["jdk-21.0.11", "jdk-21", "jdk-17", "jdk-21*"]:
            for match in java_dir.glob(folder):
                if match.is_dir() and (match / "bin" / "javac.exe").exists():
                    return match
        for sub in sorted(java_dir.iterdir(), reverse=True):
            if sub.is_dir() and (sub / "bin" / "javac.exe").exists():
                name = sub.name.lower()
                if "24" not in name and "25" not in name:
                    return sub

    return None

def ensure_local_properties(sdk_path):
    """Cria ou atualiza o arquivo local.properties."""
    local_props = PROJECT_ROOT / "local.properties"
    formatted_path = str(sdk_path).replace("\\", "\\\\").replace(":", "\\:")
    content = f"# Gerado automaticamente pelo script de inicialização\nsdk.dir={formatted_path}\n"
    
    with open(local_props, "w", encoding="utf-8") as f:
        f.write(content)
    print_ok(f"Arquivo local.properties configurado com: {sdk_path}")

def fix_gradle_properties_if_needed(jdk_path):
    """Garante que org.gradle.java.home aponte para um caminho existente no PC atual."""
    props_file = PROJECT_ROOT / "gradle.properties"
    if not props_file.exists():
        return

    try:
        with open(props_file, "r", encoding="utf-8") as f:
            lines = f.readlines()

        changed = False
        new_lines = []
        for line in lines:
            if line.strip().startswith("org.gradle.java.home="):
                current_val = line.split("=", 1)[1].strip()
                if not Path(current_val).exists() and jdk_path:
                    new_val = str(jdk_path).replace("\\", "/")
                    new_lines.append(f"org.gradle.java.home={new_val}\n")
                    changed = True
                    continue
            new_lines.append(line)

        if changed:
            with open(props_file, "w", encoding="utf-8") as f:
                f.writelines(new_lines)
            print_ok(f"gradle.properties atualizado para o JDK deste computador: {jdk_path}")
    except Exception as e:
        print_warn(f"Não foi possível ajustar gradle.properties: {e}")

def run_cmd(cmd, env=None, cwd=PROJECT_ROOT):
    """Executa um comando no shell e retorna código de saída."""
    full_env = os.environ.copy()
    if env:
        full_env.update(env)
    proc = subprocess.Popen(
        cmd,
        shell=True,
        cwd=cwd,
        env=full_env,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        encoding="utf-8",
        errors="replace"
    )
    for line in proc.stdout:
        print("    " + line.rstrip())
    proc.wait()
    return proc.returncode

def get_devices(adb_path):
    """Retorna lista de tuplas (serial, is_emulator)."""
    res = subprocess.run([str(adb_path), "devices"], capture_output=True, text=True)
    lines = res.stdout.strip().splitlines()
    devices = []
    for line in lines[1:]:
        parts = line.strip().split()
        if len(parts) >= 2 and parts[1] == "device":
            serial = parts[0]
            is_emulator = serial.startswith("emulator-") or "localhost:" in serial or "127.0.0.1:" in serial
            devices.append((serial, is_emulator))
    return devices

def find_or_download_scrcpy():
    """Localiza scrcpy ou baixa a versão portátil para a pasta scrcpy/."""
    # 1. No PATH do sistema
    which_scrcpy = shutil.which("scrcpy")
    if which_scrcpy:
        return Path(which_scrcpy)

    # 2. Na pasta local do projeto (scrcpy/ ou scrcpy-*/)
    for candidate in PROJECT_ROOT.glob("scrcpy*"):
        if candidate.is_dir():
            exe = candidate / "scrcpy.exe"
            if exe.exists():
                return exe
            for sub in candidate.iterdir():
                if sub.is_dir() and (sub / "scrcpy.exe").exists():
                    return sub / "scrcpy.exe"

    target_dir = PROJECT_ROOT / "scrcpy"
    zip_dest = PROJECT_ROOT / "scrcpy-temp.zip"

    print_step("scrcpy", "Baixando scrcpy portátil para espelhamento de tela do celular...")
    try:
        urllib.request.urlretrieve(SCRCPY_DOWNLOAD_URL, str(zip_dest))
        with zipfile.ZipFile(str(zip_dest), 'r') as zip_ref:
            zip_ref.extractall(str(PROJECT_ROOT))
        if zip_dest.exists():
            zip_dest.unlink()

        # Localiza o executável extraído
        for candidate in PROJECT_ROOT.glob("scrcpy*"):
            if candidate.is_dir():
                exe = candidate / "scrcpy.exe"
                if exe.exists():
                    print_ok(f"scrcpy instalado com sucesso em: {candidate}")
                    return exe
                for sub in candidate.iterdir():
                    if sub.is_dir() and (sub / "scrcpy.exe").exists():
                        return sub / "scrcpy.exe"
    except Exception as e:
        print_warn(f"Não foi possível baixar scrcpy automaticamente ({e}).")
        print("       Você pode baixar manualmente em: https://github.com/genymobile/scrcpy/releases")

    return None

def start_screen_mirroring(scrcpy_exe, serial, adb_path):
    """Inicia o espelhamento de tela via scrcpy para o dispositivo USB."""
    print_step("scrcpy", f"Iniciando transmissão de tela para o dispositivo USB ({serial})...")
    scrcpy_dir = scrcpy_exe.parent
    cmd = [
        str(scrcpy_exe),
        "-s", serial,
        "--window-title=C.A.S.A. Mobile - Tela do Celular (USB)"
    ]
    env = os.environ.copy()
    if adb_path and adb_path.exists():
        env["ADB"] = str(adb_path)

    try:
        subprocess.Popen(
            cmd,
            cwd=str(scrcpy_dir),
            env=env,
            creationflags=subprocess.CREATE_NEW_CONSOLE if os.name == 'nt' else 0
        )
        print_ok("Janela do scrcpy aberta com sucesso! A tela do celular está sendo transmitida.")
    except Exception as e:
        print_error(f"Erro ao abrir scrcpy: {e}")

def main():
    print_header("PROJETO C.A.S.A. - SETUP E EXECUÇÃO AUTOMÁTICA")

    # Passo 1: Localizar Android SDK
    print_step(1, "Detectando Android SDK...")
    sdk_path = find_android_sdk()
    if not sdk_path:
        print_error("Android SDK não encontrado!")
        print("       Instale o Android Studio ou defina a variável ANDROID_HOME.")
        return 1
    print_ok(f"Android SDK localizado: {sdk_path}")
    ensure_local_properties(sdk_path)

    # Passo 2: Localizar JDK compatível
    print_step(2, "Detectando Java JDK compatível...")
    jdk_path = find_jdk()
    if jdk_path:
        print_ok(f"JDK compatível localizado: {jdk_path}")
        fix_gradle_properties_if_needed(jdk_path)
    else:
        print_warn("JDK compatível não encontrado nas pastas comuns. O Gradle tentará usar o Java padrão.")

    env = {}
    if jdk_path:
        env["JAVA_HOME"] = str(jdk_path)
        env["PATH"] = f"{jdk_path / 'bin'};{os.environ.get('PATH', '')}"

    # Passo 3: Localizar ADB e verificar dispositivos
    print_step(3, "Verificando dispositivo Android ou emulador...")
    adb_path = sdk_path / "platform-tools" / "adb.exe"
    emulator_path = sdk_path / "emulator" / "emulator.exe"

    has_adb = adb_path.exists()
    devices = []
    if has_adb:
        devices = get_devices(adb_path)

    if not devices and has_adb and emulator_path.exists():
        res = subprocess.run([str(emulator_path), "-list-avds"], capture_output=True, text=True)
        avds = [a.strip() for a in res.stdout.splitlines() if a.strip()]
        if avds:
            print_warn(f"Nenhum dispositivo conectado. Iniciando emulador '{avds[0]}' automaticamente...")
            subprocess.Popen([str(emulator_path), "-avd", avds[0]], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            print("    Aguardando inicialização do emulador (pode levar alguns segundos)...")
            subprocess.run([str(adb_path), "wait-for-device"])
            time.sleep(5)
            devices = get_devices(adb_path)
            if devices:
                print_ok(f"Emulador conectado: {devices[0][0]}")
        else:
            print_warn("Nenhum emulador AVD configurado e nenhum aparelho conectado via USB.")
            print("       Dica: Conecte o celular com Depuração USB ativada ou inicie um emulador no Android Studio.")
    elif devices:
        nomes = [f"{d[0]} ({'Emulador' if d[1] else 'USB Físico'})" for d in devices]
        print_ok(f"Dispositivo(s) detectado(s): {', '.join(nomes)}")
    else:
        print_warn("Nenhum dispositivo encontrado no momento. O app será apenas compilado.")

    # Passo 4: Baixar dependências e compilar
    print_step(4, "Baixando dependências e compilando o projeto via Gradle...")
    gradlew = str(PROJECT_ROOT / "gradlew.bat")
    gradle_args = "assembleDebug"
    if jdk_path:
        jdk_arg = str(jdk_path).replace("\\", "/")
        gradle_cmd = f'"{gradlew}" -Dorg.gradle.java.home="{jdk_arg}" {gradle_args}'
    else:
        gradle_cmd = f'"{gradlew}" {gradle_args}'

    ret = run_cmd(gradle_cmd, env=env)
    if ret != 0:
        print_error("Falha ao compilar o projeto. Verifique os logs acima.")
        return ret
    print_ok("Projeto compilado com sucesso (APK gerado)!")

    # Passo 5: Instalar, rodar e transmitir via scrcpy se for USB
    if devices and has_adb:
        # Prioriza dispositivo físico USB caso exista
        physical_devices = [d for d in devices if not d[1]]
        target_device = physical_devices[0] if physical_devices else devices[0]
        serial, is_emulator = target_device

        tipo_str = "Emulador" if is_emulator else "Celular Físico USB"
        print_step(5, f"Instalando e executando no {tipo_str} '{serial}'...")
        apk_path = PROJECT_ROOT / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"
        if apk_path.exists():
            print("    Instalando APK...")
            install_res = subprocess.run([str(adb_path), "-s", serial, "install", "-r", str(apk_path)], capture_output=True, text=True)
            if install_res.returncode == 0:
                print_ok("APK instalado com sucesso!")
                print(f"    Iniciando {APP_PACKAGE}{MAIN_ACTIVITY}...")
                subprocess.run([str(adb_path), "-s", serial, "shell", "am", "start", "-n", f"{APP_PACKAGE}/{MAIN_ACTIVITY}"])
                print_ok("Aplicativo iniciado com sucesso na tela!")

                # Se for USB físico, abre o espelhamento com scrcpy
                if not is_emulator:
                    scrcpy_exe = find_or_download_scrcpy()
                    if scrcpy_exe:
                        start_screen_mirroring(scrcpy_exe, serial, adb_path)
                    else:
                        print_warn("scrcpy não disponível para espelhar a tela. O app está rodando normalmente no celular.")
            else:
                print_error(f"Erro ao instalar APK: {install_res.stderr or install_res.stdout}")
        else:
            print_warn("APK não encontrado no caminho padrão após compilação.")

    print_header("PROCESSO CONCLUÍDO COM SUCESSO!")
    print("  O projeto está pronto e sincronizado.")
    print("=" * 65 + "\n")
    return 0

if __name__ == "__main__":
    sys.exit(main())
