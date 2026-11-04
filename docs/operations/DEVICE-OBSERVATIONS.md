# Observações de validação no aparelho

Este arquivo registra somente resultados técnicos. Não devem ser incluídos valores, nomes, textos de notificações ou outros dados financeiros reais.

## 3 de outubro de 2026 — aparelho Android de teste

- O aparelho carrega quando conectado ao computador, mas o Windows não enumera uma interface MTP, ADB ou dispositivo desconhecido.
- `adb devices -l` e a descoberta ADB por mDNS ainda não listam o aparelho.
- A primeira tentativa de instalação manual exibiu apenas “O app não foi instalado”. Uma segunda captura revelou a causa: o Google Play Protect bloqueou o APK porque ele pode solicitar acesso a dados sensíveis. Neste protótipo, o gatilho esperado é o serviço de acesso às notificações, que constitui a funcionalidade central do diagnóstico.
- O mesmo APK foi novamente validado no computador: SHA-256 `0f5d5c1f8ec3fa1131b02827a957819b30118f656bb459f3c450b922e096a6dd`, assinatura APK v2 válida, `minSdk` 26 e `targetSdk` 37.
- O bloqueio é preventivo e não demonstra que o APK contenha código malicioso. O manifesto não declara acesso à Internet, e a leitura de notificações é filtrada para o pacote `com.picpay`; ainda assim, APKs externos com acesso a notificações pertencem a uma classe de risco usada em fraudes financeiras e são bloqueados pelo Play Protect.

### Próximo diagnóstico

O aparelho de teste foi pareado com sucesso pelo ADB por Wi-Fi e usava Android 16/API 36. A instalação do mesmo APK por `adb install -r` retornou `Success`, confirmando que o bloqueio observado se limitava ao fluxo de instalação manual analisado pelo Play Protect.

A atividade `br.com.financasconscientes.diagnostic/.MainActivity` abriu com estado `COLD`, o pacote foi encontrado em `/data/app/` e o processo do aplicativo permaneceu ativo. O serviço `PicPayNotificationListener` está registrado com `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`. O acesso às notificações ainda não foi concedido e depende de confirmação manual do usuário no aparelho.

Depois da instalação, o Android também pode classificar o acesso às notificações como configuração restrita. Nesse caso, o usuário deve abrir as informações do app, revisar sua origem e finalidade e usar “Permitir configurações restritas” antes de conceder manualmente o acesso às notificações. Essa autorização nunca deve ser automatizada.

Referências:

- [Como o Google Play Protect protege apps e dados](https://support.google.com/android/answer/2812853)
- [Configurações restritas no Android](https://support.google.com/android/answer/12623953)
