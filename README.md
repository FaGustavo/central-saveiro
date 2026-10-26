# Central Saveiro

Personalização completa de uma central multimídia Android genérica (chip **Autochips AC8227L**, Android 8.1, 2 GB de RAM, tela 1280x720) instalada numa VW Saveiro. Tudo foi feito **sem root**, pelo ADB e por um app próprio com permissão de sistema.

---

## O que mudou na central (para quem usa)

**Tela inicial nova**
- Tela inicial própria e limpa: relógio, data, tensão da bateria e um cartão grande com o que está tocando.
- O cartão mostra a capa do álbum, o nome da música e botões grandes de voltar, tocar/pausar e avançar. Funciona com Spotify, Mídia (pendrive), Bluetooth e Rádio.
- O fundo da tela fica com a capa da música desfocada.
- Uma Saveiro em neon ao lado do player, na cor da música que está tocando.
- Seis ícones grandes no mesmo estilo (BT Música, DiPlay, Mídia, Rádio, Spotify, Ajustes), que também mudam de cor com a música.
- Gaveta "Ajustes" com Definições, Bluetooth, Volante, Veículo, Som, Arquivos e Luzes.
- Sem barra de status. Ela aparece arrastando o dedo de cima para baixo.

**Música e rádio**
- **Mídia**: biblioteca de músicas e vídeos do pendrive (artistas, álbuns, faixas, listas, vídeos), no mesmo visual da central.
- **Vídeos**: player próprio em tela cheia que, ao sair, continua tocando no cartão da tela inicial (mini player 16:9). Usa o decodificador de hardware: converta os vídeos para .mp4 H.264 720p (os .mkv VP9 do YouTube vão por software e pesam no processador).
- **Rádio** novo: frequência grande, nome da estação, régua para arrastar, busca de estações e 6 memórias. Cada estação tem uma cor própria.
- **BT Música** nova: música, artista, álbum e progresso do celular conectado. Mostra a capa real quando o Spotify da central está espelhando o celular.
- **Spotify** instalado e otimizado.

**Som**
- App **Som** com grave, médio, agudo, loudness, subwoofer (nível e corte), balanço e fader. Ele substitui o DSP de fábrica, que tinha um equalizador que não funcionava de verdade.
- Os ajustes voltam sozinhos sempre que a central liga.

**Luzes e tela**
- LEDs dos botões na cor da capa da música ou em cor fixa à escolha.
- Cores da tela mais vivas e pretos mais escuros.
- **Modo noturno** por horário: a tela e os LEDs ficam bem fracos à noite. Ao tocar na tela, o brilho sobe por alguns segundos, sempre com transição suave.

**Segurança e praticidade**
- Aviso grande em vermelho na tela quando a bateria do carro cai abaixo de 11,5 V.
- Logo de abertura com uma foto da própria Saveiro.
- CarPlay e Android Auto sem fio pelo **DiPlay**, com imagem bem melhor que a do app de fábrica.
- Hora certa no fuso de Brasília.

**Mais leve**
- Apps desnecessários desativados (Google, GPS, logs de depuração, apps duplicados). Sobrou bem mais memória livre.

---

## Detalhes técnicos (para quem desenvolve)

**Acesso de sistema sem root**
- Os apps de sistema da ROM são assinados com a **chave de plataforma pública de teste do AOSP**. O app próprio `com.fantinel.logocopy` é assinado com essa chave e usa `sharedUserId="android.uid.system"` (uid 1000).
- O build é manual: `javac` (release 8), `d8`, `aapt2`, `zipalign` e `apksigner`, no script `codigo/build-logocopy.ps1`. As classes anônimas quebram o `d8` antigo, então o script empacota tudo num `.jar` antes.

**App próprio (activities e serviço)**
- `Home`: launcher (`category.HOME`). A fonte atual vem do callback do serviço `sourceinfo` (`ISourceService`). Metadados das MediaSessions. Rádio via `android.radio.RadioPlayer`, por reflexão.
- `Radio`, `BtMusica`, `Midia`, `Som`, `Luzes`: telas próprias. A `Midia` usa o `MediaBrowserService` do VLC (`org.videolan.vlc/.PlaybackService`) como motor.
- `Guard`: serviço iniciado no boot. Mantém `service.zlink.enable=0` (o z-link travava a USB em modo host e derrubava o ADB), reaplica som e tela, lê a bateria e gerencia o modo noturno.
- `Main`: modos utilitários chamados por `am start --es mode ...` (prop, comp, fx, tela, tz, fonte, testbat, reflect…).

**Hardware e serviços da ROM usados**
- Chip de som **CD3313** via `android.audio.AudioEffect` (`setBassBoost`, `setTreble`, `setSubWoofer`, `setBalance`…).
- LEDs dos botões: serviço `carstatus`, transação 8 `setLed(0, r, g, b)`, com correção de gama 2.2. Fisicamente é uma zona só.
- Tensão da bateria: `mcu_service` → `IMcuManager.getBattery()`.
- Saturação, contraste e brilho por software: transação 1015 do `SurfaceFlinger` (matriz de cor 4x4).
- Troca de fonte sem abrir o app de fábrica: `android.sourceservice.SourceInfo.onRequestPlayAudio(context do pacote)`. No Bluetooth, a chamada usa o `ComponentName` da tela `com.autochips.bluetooth/.BtMusicActivity`: só assim a ROM muda o caminho de áudio para A2DP e libera o foco de áudio ao Bluetooth.
- Toques em qualquer app para o modo noturno: janela invisível de 1 px com `FLAG_WATCH_OUTSIDE_TOUCH`.

**Ajustes de sistema via ADB**
- `pm disable-user` em apps do Google, mtklogger, autocan, atcLocationService, ZLink, DSP e launcher antigos (todos reversíveis).
- `settings put global policy_control immersive.status=*` (barra de status escondida).
- `cmd package compile -m speed-profile` no Spotify e no VLC. O modo `speed` falha por falta de RAM.
- Fuso `America/Fortaleza` (a base de fusos da ROM ainda aplica horário de verão em São Paulo).

**Limitações conhecidas**
- Wi‑Fi só em 2.4 GHz: CarPlay sem fio com um pouco de atraso.
- O Bluetooth do Android 8.1 não recebe capa de álbum.

---

## Estrutura

```
APLICAR-TUDO.ps1     reaplica tudo depois de um reset de fábrica
LEIAME.txt           guia completo: o que foi feito, como desfazer, cuidados
codigo/              código-fonte do app próprio, recursos e script de build
imagens/             logo de abertura e Saveiro neon
apks/LogoCopy.apk    app próprio compilado
```

Ficam fora do repositório, porque são de terceiros: os APKs do Spotify, VLC e DiPlay e as ferramentas do Android SDK (`aapt2`, `d8`, `zipalign`, `apksigner`, `android.jar`). Para recompilar, coloque essas ferramentas em `ferramentas/`.
