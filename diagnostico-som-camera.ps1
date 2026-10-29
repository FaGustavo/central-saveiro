# ============================================================
#  Diagnostico da camera de re e do som (SO LEITURA: nao altera nada na central)
#  Uso: ligar a central no PC pelo USB (carro com chave ligada) e rodar:
#     & "C:\Users\Fantinel\Documents\Central-Saveiro\diagnostico-som-camera.ps1"
#  Gera diagnostico\<data>.txt dentro da pasta do projeto.
# ============================================================
$adb = 'C:\platform-tools\adb.exe'
$dir = Join-Path $PSScriptRoot 'diagnostico'
New-Item -ItemType Directory -Force $dir | Out-Null
$out = Join-Path $dir ("diag-{0:yyyyMMdd-HHmmss}.txt" -f (Get-Date))

if (-not ((& $adb devices) -match "`tdevice")) { Write-Host 'Central nao encontrada no ADB. Confira o cabo e a Depuracao USB.'; exit 1 }

function Run([string]$Title, [string]$Cmd) {
    Add-Content $out "`n===== $Title =====`n> adb shell $Cmd"
    & $adb shell $Cmd 2>&1 | Out-String | Add-Content $out
}

Write-Host 'Coletando informacoes...'
# --- Camera de re
Run 'Propriedades (camera/video/re)' "getprop | grep -iE 'cam|cvbs|ahd|avin|tvd|rev|back|video|nvp|tp28|pr20'"
Run 'Settings system (camera/re)'   "settings list system | grep -iE 'cam|cvbs|ahd|avin|rev|back|video|mirror|guide|track'"
Run 'Settings global (camera/re)'   "settings list global | grep -iE 'cam|cvbs|ahd|avin|rev|back|video'"
Run 'Settings secure (camera/re)'   "settings list secure | grep -iE 'cam|cvbs|ahd|avin|rev|back|video'"
Run 'App da camera'                 "dumpsys package com.auto.hcamera | grep -iE 'versionName|Activity|Service|enabled|disabled'"
Run 'Dispositivos de video'         'ls -l /dev/video* /dev/tvd* /dev/*cvbs* /dev/*ahd* 2>/dev/null'
Run 'Camera (media.camera)'         'dumpsys media.camera | head -n 60'
Run 'Arquivos de config da camera'  "ls -l /system/etc/*cam* /vendor/etc/*cam* /system/etc/*ahd* /vendor/etc/*ahd* /data/*cam* 2>/dev/null"
Run 'Apps de fabrica/ajustes'       "pm list packages | grep -iE 'hcn|auto|setting|factory|camera|carsetting|mcu'"

# --- Som
Run 'Efeitos de audio do Android'   'cat /vendor/etc/audio_effects.conf /system/etc/audio_effects.conf /vendor/etc/audio_effects.xml /system/etc/audio_effects.xml 2>/dev/null'
Run 'Bibliotecas de efeito'         'ls /system/lib/soundfx /vendor/lib/soundfx 2>/dev/null'
Run 'AudioFlinger (efeitos ativos)' "dumpsys media.audio_flinger | grep -iE -A3 'effect|Global session'"
Run 'Saidas de audio'               "dumpsys media.audio_policy | grep -iE 'device|output' | head -n 40"
Run 'Settings de som'               "settings list system | grep -iE 'sub|eq|bass|treble|alto|mid|loud|fader|balance|audio'"

# --- Log enquanto a re e engatada
& $adb logcat -c
Write-Host ''
Write-Host 'AGORA: engate a re (motor ligado ou chave na posicao de ligar), espere a tela da camera,'
Write-Host 'e depois de uns 10 segundos tire da re. Aperte ENTER quando terminar.'
[void](Read-Host)
Add-Content $out "`n===== LOG durante a re ====="
& $adb logcat -d -v time 2>&1 | Select-String -Pattern 'cam|cvbs|ahd|avin|tvd|rev|backcar|video|decoder|signal|hcamera' -CaseSensitive:$false |
    ForEach-Object { $_.Line } | Add-Content $out

Write-Host "Pronto. Resultado em: $out"
