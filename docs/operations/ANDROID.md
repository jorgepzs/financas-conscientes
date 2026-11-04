# Compilar, instalar e validar no Android

## Requisitos de desenvolvimento

- Android Studio em versão compatível com AGP 9.4.
- JDK 17 (normalmente fornecido pelo Android Studio).
- Gradle 9.6.0, Android SDK Platform 37 e Android SDK Build Tools 36.0.0.
- Aparelho Android com opções de desenvolvedor e depuração USB, se instalar por ADB.

O projeto usa Android Gradle Plugin 9.4.1 e Kotlin integrado ao AGP. Não aplicamos `org.jetbrains.kotlin.android` em separado: o AGP 9.x ativa Kotlin integrado e inclui a versão padrão do Kotlin Gradle Plugin. O JDK de build é 17.

Este ambiente está configurado com JDK 17, Android Studio, Android SDK e ADB. Abra diretamente `repositories/app-android/` no Android Studio, aguarde a sincronização Gradle e compile a variante `debug`.

Nesta máquina, o Windows Application Control bloqueia a cópia do AAPT2 obtida do cache Maven do Gradle. O arquivo de usuário `%USERPROFILE%\.gradle\gradle.properties` aponta `android.aapt2FromMavenOverride` para a cópia oficial instalada pelo Android SDK Manager. Não remova essa configuração enquanto a política continuar bloqueando a ferramenta do cache.

A versão estável anteriormente instalada e preservada para teste é Android 0.5.2. A implementação experimental 0.6.5 inclui resumo mensal dinâmico para decisões personalizadas, categorias configuráveis, aparência claro/escuro/automática e lembretes opcionais do PicPay. Consulte [V0.6.1](V0.6-experimental.md), [V0.6.2](V0.6.2.md), [V0.6.3](V0.6.3.md), [V0.6.4](V0.6.4.md) e [V0.6.5](V0.6.5.md).

## Compilar pelo terminal

Com JDK 17 disponível no `PATH`, a partir de `repositories/app-android/`:

```powershell
.\gradlew.bat assembleDebug
```

O APK aparece em `app/build/outputs/apk/debug/app-debug.apk`. O wrapper versionado fixa Gradle 9.6.0 e valida o SHA-256 oficial da distribuição.

## Instalar por ADB

```powershell
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Também é possível executar `app` pelo Android Studio com o aparelho conectado.

### Quando o Play Protect bloqueia a instalação manual

O build de diagnóstico declara um `NotificationListenerService`, pois precisa observar alertas do PicPay. O Play Protect pode bloquear preventivamente APKs obtidos por navegador, mensageiro ou gerenciador de arquivos quando eles solicitam acesso a notificações ou outros dados sensíveis usados com frequência em fraude financeira.

Para desenvolvimento local, prefira ADB por USB ou a depuração sem fio. Não desative globalmente o Play Protect. Se o Android restringir o acesso às notificações mesmo após a instalação, abra **Informações do app**, revise o aplicativo e use o menu para **Permitir configurações restritas**; depois conceda o acesso às notificações manualmente.

O aplicativo foi projetado para reduzir esse risco: não declara permissão de Internet, filtra o pacote `com.picpay` antes de ler os campos da notificação, cifra os eventos localmente, limita retenção e permite apagar o histórico. Esses controles não substituem a revisão do usuário antes de conceder o acesso.

## Conceder acesso ao listener

1. Abra o app “Finanças Conscientes — Diagnóstico”.
2. Toque em “Abrir acesso a notificações”.
3. Na tela do Android, encontre o app e ative “Permitir acesso às notificações”.
4. Confirme o aviso do Android após ler o escopo.
5. Volte ao app e verifique que o estado mudou para “Ativo”.
6. Faça uma movimentação que você já pretendia realizar e aguarde o alerta do PicPay.
7. Observe os campos locais; não compartilhe screenshots ou texto integral sem remover valores e nomes.

O acesso é concedido manualmente pelo usuário. O serviço processa somente notificações cujo pacote é `com.picpay`.

## Fabricantes Android e execução em segundo plano

Primeiro valide sem modificar controles de bateria. Se o Android deixar de entregar alertas após um período, registre esse comportamento antes de ajustar o sistema. Nas configurações do fabricante, procure opções de bateria e inicialização automática para este app e permita atividade em segundo plano somente se necessário. Os nomes de menus variam por versão do sistema.

## Limpar o histórico

Use “Apagar histórico” no app. Isso exclui o arquivo local cifrado e a chave do Android Keystore usada por ele.

## Diagnóstico básico

- **Estado desativado:** ative o acesso do app nas configurações especiais do Android e volte à tela.
- **Nenhum evento aparece:** confirme que as notificações do PicPay estão habilitadas, que o app emite alerta para a operação e que o pacote instalado é `com.picpay`.
- **Texto parcial:** o Android pode fornecer texto oculto ou resumido; isso é limite da notificação, não falha de extração.
- **Build falha:** confirme JDK 17, Android SDK Platform 37, Build Tools 36.0.0, Gradle 9.6.0 e acesso aos repositórios Maven/Google para resolver plugins.

## Segurança do dispositivo

Use um bloqueio de tela. O histórico é cifrado no armazenamento do app, não no backup. Não compartilhe o APK com dados de diagnóstico reais. Um build debug não foi assinado para distribuição pública.
