# Reaplica na central tudo o que foi feito (ex.: depois de um reset de fábrica).
# Uso: ligar a central no PC pelo USB com depuração USB ativa e rodar:
#   powershell -ExecutionPolicy Bypass -File "C:\Users\Fantinel\Documents\Central-Saveiro\APLICAR-TUDO.ps1"
$d = $PSScriptRoot
$a = "C:\platform-tools\adb.exe"
function sh($cmd) { & $a shell $cmd }
function lc($args2) { & $a shell "am start -n com.fantinel.logocopy/.Main $args2" | Out-Null; Start-Sleep 2 }

Write-Output "Esperando a central..."; & $a wait-for-device

Write-Output "1) Ajustes de desempenho"
sh "settings put global window_animation_scale 0.5; settings put global transition_animation_scale 0.5; settings put global animator_duration_scale 0.5"
sh "settings put global wifi_scan_always_enabled 0; settings put global ble_scan_always_enabled 0"
# GPS/localizacao desligados (CarPlay/Android Auto usam o GPS do celular)
sh "settings put secure location_providers_allowed -gps; settings put secure location_providers_allowed -network; settings put secure location_mode 0"
# barra de status escondida em todos os apps (deslizar de cima para ver; desfazer: settings put global policy_control null)
sh "settings put global policy_control immersive.status=*"

Write-Output "2) Instalando apps"
& $a install -r "$d\apks\LogoCopy.apk"
& $a install -r "$d\apks\VLC-3.7.1-armeabi-v7a.apk"
& $a install -r "$d\apks\DiPlay-0_2_21-HUD.apk"
& $a install -r "$d\apks\Spotify-9.1.94.apk"
# fuso de Brasilia (Fortaleza = UTC-3 sem horario de verao; o tzdata da central e antigo)
& $a shell "am start -S -n com.fantinel.logocopy/.Main --es mode tz --es tz America/Fortaleza" | Out-Null
sh "pm grant org.videolan.vlc android.permission.READ_EXTERNAL_STORAGE; pm grant org.videolan.vlc android.permission.WRITE_EXTERNAL_STORAGE"
sh "cmd package compile -m speed-profile -f org.videolan.vlc"

Write-Output "3) Desativando apps (reversivel com 'pm enable')"
$off = @(
  # Google
  "com.google.android.youtube","com.google.android.apps.maps","com.google.android.googlequicksearchbox",
  "com.android.vending","com.android.chrome","com.google.android.apps.pdfviewer","com.google.android.feedback",
  "com.google.android.backuptransport","com.google.android.configupdater","com.google.android.partnersetup",
  "com.google.android.printservice.recommendation","com.google.android.syncadapters.contacts","com.google.android.tts",
  "com.google.android.onetimeinitializer","com.google.android.gsf","com.google.android.gms",
  # fabricante
  "com.mediatek.mtklogger","com.mediatek.mtklogger.proxy","com.hcn.autocan","com.hcn.gallery",
  "com.chartcross.gpstestplus","com.hcn.autouserguide","com.hcn.applicationlogo",
  "com.zjinnova.zlink","com.hcn.autoeq","com.autochips.android.atcLocationService"
)
foreach ($p in $off) { sh "pm disable-user --user 0 $p" }
# o radio de fabrica nao pode ser encerrado por ocio (senao o FM fica mudo apos ~1 min)
sh "dumpsys deviceidle whitelist +com.hcn.autoradio"

Write-Output "4) Escondendo icones (app continua instalado)"
lc "--es mode comp --es comp com.auto.hcamera/com.auto.hcamera.VideoInActivity --ez on false"
lc "--es mode comp --es comp com.hcn.AutoMediaPlayer/com.hcn.MediaActivity.MusicPlayerUiActivity --ez on false"
lc "--es mode comp --es comp com.hcn.AutoMediaPlayer/com.hcn.MediaActivity.VideoPlayerUiActivity --ez on false"

Write-Output "5) Vigia do z-link (pode derrubar o ADB por alguns segundos)"
& $a shell "am start -S -n com.fantinel.logocopy/.Main --es mode zlink_off" | Out-Null
Start-Sleep 5; & $a wait-for-device

Write-Output "5b) Cores da tela mais vivas (saturacao 1.35, contraste 1.1)"
& $a shell "am start -S -n com.fantinel.logocopy/.Main --es mode tela --ef s 1.35 --ef c 1.1" | Out-Null
Start-Sleep 2
& $a shell "am start -n com.fantinel.logocopy/.Main --es mode zlink_off" | Out-Null

Write-Output "6) Tela inicial nova"
sh "cmd package set-home-activity com.fantinel.logocopy/.Home"
sh "pm disable-user --user 0 com.android.launcher3"
sh "am start -a android.intent.action.MAIN -c android.intent.category.HOME"

Write-Output ""
Write-Output "Pronto. Falta so (manual, ver LEIAME):"
Write-Output " - Logo de abertura: copiar imagens\logo.png para a raiz do pendrive e seguir o LEIAME"
Write-Output " - Esquecer a rede Wi-Fi de casa na central (atrapalha o CarPlay sem fio)"
Write-Output " - Ajustar o som no app Som"
Write-Output " - Spotify: entrar pelo codigo do e-mail; depois de usar um pouco rodar:"
Write-Output "     adb shell cmd package compile -m speed-profile -f com.spotify.music"
Write-Output "   (NAO usar -m speed: falha por falta de RAM e apaga a compilacao)"
