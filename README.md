# QuantumKey Phone

Softphone SIP Android da **QuantumKey** — chamadas de voz e vídeo pela internet com o protocolo SIP, pronto para uso com números virtuais (ex.: provedores como Telera) e ramais VoIP.

## Recursos

- 📞 Chamadas de voz e vídeo SIP (IPv4/IPv6, NAT traversal)
- 🔒 Criptografia de mídia (SRTP) e opções de criptografia ponta-a-ponta (ZRTP)
- 💬 Mensagens instantâneas e chat
- 📇 Agenda e histórico de chamadas
- 🎵 Codecs de áudio e vídeo de alta qualidade (Opus, VP8/H.264 conforme dispositivo)
- 📱 Android 8.0+

## Build

Pré-requisitos: JDK 17 e Android SDK (platform + build-tools) configurados.

```bash
./gradlew assembleDebug        # APK debug em app/build/outputs/apk/debug/
./gradlew assembleRelease      # requer keystore próprio (keystore.properties)
```

## Conectando um número SIP (ex.: Telera)

1. Contrate um número virtual no painel do provedor
2. No QuantumKey Phone: **Configurar uma conta** → **Usar um provedor SIP**
3. Informe SIP URI, usuário, senha e domínio do provedor

## Sobre o projeto

QuantumKey Phone é um **fork open source do [Linphone Android](https://github.com/BelledonneCommunications/linphone-android)** ([Belledonne Communications](https://www.linphone.org)), sob licença **GPL-3.0** (ver `LICENSE.txt`). O código original e o Linphone SDK permanecem © Belledonne Communications — a marca e a identidade visual deste app são da QuantumKey.

## Licença

[GPL-3.0](LICENSE.txt)
