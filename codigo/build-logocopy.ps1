# Compila, assina e instala o app LogoCopy (tela Início, Som, vigia do z-link).
# Precisa do Java (JDK 17+) e do ADB em C:\platform-tools.
$d = Split-Path -Parent $PSScriptRoot          # pasta Central-Saveiro
$c = "$d\codigo"; $s = "$d\ferramentas"; $o = "$c\out"
$jdk = Get-ChildItem "C:\Program Files\Microsoft\jdk-*\bin", "C:\Program Files\Java\*\bin" -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $jdk) { Write-Output "Java nao encontrado"; exit 1 }
$j = $jdk.FullName; $a = "C:\platform-tools\adb.exe"

Remove-Item -Recurse -Force $o -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force "$o\cls", "$o\dex" | Out-Null
& "$j\javac.exe" -nowarn -Xlint:-options --release 8 -cp "$s\android.jar" -d "$o\cls" (Get-ChildItem "$c\src\com\fantinel\logocopy\*.java").FullName
if ($LASTEXITCODE -ne 0) { Write-Output "ERRO javac"; exit 1 }
& "$j\jar.exe" cf "$o\classes.jar" -C "$o\cls" .
& "$j\java.exe" -cp "$s\d8.jar" com.android.tools.r8.D8 --lib "$s\android.jar" --min-api 21 --output "$o\dex" "$o\classes.jar"
if (-not (Test-Path "$o\dex\classes.dex")) { Write-Output "ERRO d8"; exit 1 }
& "$s\aapt2.exe" compile --dir "$c\res" -o "$o\res.zip"
& "$s\aapt2.exe" link -o "$o\base.apk" -I "$s\android.jar" --manifest "$c\AndroidManifest.xml" "$o\res.zip"
Push-Location "$o\dex"; & "$j\jar.exe" uf "$o\base.apk" classes.dex; Pop-Location
& "$s\zipalign.exe" -f 4 "$o\base.apk" "$o\aligned.apk"
& "$j\java.exe" -jar "$s\apksigner.jar" sign --ks "$c\platform.p12" --ks-pass pass:android --ks-key-alias platform --key-pass pass:android --out "$d\apks\LogoCopy.apk" "$o\aligned.apk"
& $a install -r "$d\apks\LogoCopy.apk"
& $a shell "am start -S -n com.fantinel.logocopy/.Main --es mode zlink_off" | Out-Null
