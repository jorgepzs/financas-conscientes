# Relatório de validação local

Data: 3 de outubro de 2026.

## Ambiente configurado

- Android Studio Rabbit 1, versão 2026.2.1.8.
- Eclipse Temurin JDK 17.0.20.1.
- Android SDK Command-line Tools 22.0.
- Android SDK Platform 37, Build Tools 36.0.0 e Platform Tools 37.0.1.
- Gradle Wrapper 9.6.0 com SHA-256 da distribuição fixado.
- Variáveis de usuário `JAVA_HOME`, `ANDROID_HOME` e `ANDROID_SDK_ROOT`, além de Java, ADB e ferramentas do SDK no `Path`.

O Windows Application Control bloqueou a cópia do AAPT2 baixada automaticamente pelo Gradle. A configuração local de Gradle usa a cópia oficial instalada pelo Android SDK Manager em `build-tools/36.0.0/aapt2.exe`; nenhuma política do Windows foi alterada.

## Escopo validado

- Estrutura da pasta-mãe e separação entre documentação e repositórios.
- Configuração Gradle do aplicativo Android.
- Manifesto, recursos XML e links locais da documentação.
- Ordem do filtro de pacote no listener.
- Ausência de permissão de rede e de credenciais embutidas.
- Controles de retenção, exclusão, backup e cifragem local por inspeção de código.

## Resultados

- 4 arquivos XML analisados estruturalmente; nenhuma falha.
- 19 arquivos Markdown; nenhum link local quebrado.
- 4 arquivos Kotlin presentes no MVP.
- O manifesto não declara `android.permission.INTERNET`.
- O listener exige `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE` e não é exportado.
- `PicPayNotificationListener` compara `sbn.packageName` com `com.picpay` antes de acessar `notification.extras`.
- O arquivo local usa AES-GCM, chave no Android Keystore, `AtomicFile`, `noBackupFilesDir`, máximo de 100 eventos e retenção de 30 dias.
- O aplicativo desabilita backup e transferência de dados por regras explícitas e bloqueia screenshots/preview da tela pelo `FLAG_SECURE`.
- `assembleDebug` concluiu com sucesso usando `compileSdk` e `targetSdk` 37.
- `lintDebug` concluiu com zero erros. Resta um aviso informativo porque Gradle 9.6.0 é a versão compatível fixada para AGP 9.4.1, embora exista uma versão Gradle mais nova.
- `testDebugUnitTest` foi executado, mas não há testes unitários nesta primeira versão.
- A assinatura debug foi validada pelo `apksigner` com APK Signature Scheme v2.
- O APK final tem 909.476 bytes e SHA-256 `0f5d5c1f8ec3fa1131b02827a957819b30118f656bb459f3c450b922e096a6dd`.
- Uma tentativa de instalação manual em aparelho Android foi bloqueada pelo Google Play Protect por potencial solicitação de acesso a dados sensíveis. O comportamento é compatível com a presença intencional de um `NotificationListenerService`. Em seguida, um aparelho de teste com Android 16/API 36 foi pareado pelo ADB por Wi-Fi; `adb install -r` retornou `Success` e a atividade principal abriu corretamente. Consulte [Observações de validação no aparelho](DEVICE-OBSERVATIONS.md).
- O acesso ao `PicPayNotificationListener` foi concedido e confirmado pelo Android. Os canais de notificação registrados por `com.picpay` estavam habilitados, mas a operação observada de Pix enviado não gerou notificação; Pix recebido gerou alerta. A arquitetura foi revisada para usar o CSV oficial do PicPay como fonte financeira e compartilhamento de comprovante como captura imediata.

## Validação ainda pendente

O ADB está funcional por Wi-Fi e a instalação em aparelho físico foi concluída. Ainda faltam:

- testes instrumentados;
- validação da interface e do ciclo completo em aparelho Android de teste;
- confirmação visual de que o evento de Pix recebido apareceu no histórico do app;
- observação dos campos disponíveis no evento de Pix recebido, sem registrar dados pessoais;
- teste de reinício e comportamento com a tela apagada.

Esses itens permanecem como critérios obrigatórios no [checklist de aceite](ACCEPTANCE-CHECKLIST.md). Build aprovado não equivale a validação funcional no aparelho.

## V2 — registro e CSV

- Módulo separado `app-v2`, pacote `br.com.financasconscientes`, versão 0.2.0.
- Nenhuma permissão declarada; sem `INTERNET` e sem `NotificationListenerService`.
- Registro manual e destino de compartilhamento para texto, imagem, PDF e CSV.
- Arquivos brutos de comprovante não são lidos; CSV é normalizado e descartado após processamento.
- Estado cifrado localmente com AES-GCM e chave no Android Keystore; backup e transferência desabilitados.
- Parser ajustado à amostra real do PicPay sem copiar seu conteúdo ao projeto: UTF-8 BOM, vírgula, data/hora separadas, moeda brasileira e sinal de menos Unicode.
- Fingerprint preserva linhas idênticas legítimas e evita duplicação ao importar novamente o mesmo extrato.
- Seis testes unitários passaram, incluindo um smoke test privado da amostra real; nenhum teste registrou conteúdo do arquivo.
- `lintDebug` concluiu com zero erros e zero avisos.
- APK v2 verificado com assinatura debug v2, 952.711 bytes, SHA-256 `4b26dbc58a8aeef802fd36a3f81e9a64aa7feccd5d37998d20bbb7266a48a78d`.
- Instalação e abertura em aparelho Android de teste com Android 16/API 36, concluídas com sucesso por ADB sem fio.
- O sistema confirmou que a V2 aparece como destino de compartilhamento para PDF, imagem e CSV.
- `FLAG_SECURE` bloqueou a captura da interface como esperado.

Ainda dependem de aceite manual: importar o CSV pelo seletor no aparelho, comparar a contagem com o PicPay, classificar uma pendência e compartilhar um comprovante existente.

### Persistência confirmada no aparelho

Em uma validação local, o armazenamento cifrado permaneceu inalterado após `force-stop` e uma abertura fria da atividade, e o processo iniciou normalmente. Nenhum identificador ou hash de estado financeiro é publicado neste relatório.

Persistem como aceite manual final a comparação da contagem com o extrato do PicPay e o fluxo de compartilhamento de um comprovante já existente.

## V3 — painel mensal, orçamento e revisão

- Pacote mantido em `br.com.financasconscientes`, versão elevada para 0.3.0.
- Painel mensal calcula saídas, orçamento, pendências, planejado/impulso e categorias exclusivamente a partir dos valores confirmados pelo CSV.
- Registros manuais ou de comprovantes sem vínculo permanecem provisórios e não entram em totais.
- Classificações podem ser editadas ou excluídas individualmente; excluir uma vinculada devolve a transação à fila de pendências.
- O esquema cifrado passou à versão 2 com orçamento opcional e leitura retrocompatível do estado anterior.
- Oito testes unitários passaram, inclusive o smoke test privado do CSV real, sem registrar seu conteúdo.
- `lintDebug` e `assembleDebug` concluíram com sucesso.
- APK assinado pelo mesmo certificado debug, com APK Signature Scheme v2, 975.617 bytes e SHA-256 `c52e00b12f5fa3fd874990c459dbdcb3c0215305310fd3510da6aeb30747cb79`.
- A instalação com `adb install -r` atualizou 0.2.0 para 0.3.0 e a atividade iniciou em aparelho Android de teste.
- O arquivo privado permaneceu idêntico antes e depois da atualização; seu hash e conteúdo não são publicados.

Permanecem como aceite manual a conferência visual dos totais e os fluxos de orçamento, edição e exclusão no aparelho.

## V4 — comprovantes e exclusão recuperável do extrato

- Versão 0.4.0 mantém o pacote `br.com.financasconscientes`, sem permissões declaradas.
- A tela inicial foi reorganizada em dois passos: captura pelo comprovante e confirmação pelo CSV.
- O recebimento mostra tipo/nome disponível do comprovante, exige valor para conciliação e confirma que o arquivo bruto não é guardado.
- Movimentações importadas podem ser apagadas individualmente ou em conjunto; classificações vinculadas são desvinculadas e preservadas como provisórias.
- Dez testes unitários passaram, incluindo remoção individual, remoção total, painel e parser do CSV real sem registrar conteúdo.
- `lintDebug` concluiu sem problemas e `assembleDebug` concluiu com sucesso.
- O artefato final com a nova interface possui assinatura debug v2 válida, 994.439 bytes e SHA-256 `9ac324b9d88246a29fb5f58722900e8990d9b641d07aa9f86b7f21b164381fd6`.
- O artefato final 0.4.0 com a nova interface foi instalado com `adb install -r`, que retornou `Success`. Por solicitação do usuário, o aplicativo não foi aberto nem operado durante esta etapa.
- O hash do arquivo cifrado foi idêntico antes e depois da instalação final. Nenhuma função de exclusão foi executada.

Aceite manual pendente: revisão visual da nova interface, compartilhamento de um comprovante real já existente e teste de exclusão/reimportação com uma movimentação que o usuário escolha.

## V4.1 — menu superior

- Versão elevada para 0.4.1 (`versionCode` 5).
- Menu de três pontos no cabeçalho oferece sete atalhos: comprovante, registro manual, importar CSV, gerenciar extrato, orçamento, privacidade e apagar todos os dados.
- Dez testes unitários passaram; `lintDebug` não encontrou problemas; `assembleDebug` concluiu com sucesso.
- APK debug v2 válido, 964.732 bytes e SHA-256 `a4403e56ed13ea2bba4d0a658b29594e8def6be1bbbbbff6a407425f35e47ed3`.
- Instalação sobre a 0.4.0 retornou `Success`; o arquivo cifrado manteve o mesmo hash antes e depois.
- A atividade iniciou, mas a cortina de notificações estava em primeiro plano. Nenhum item do menu foi acionado e nenhuma informação financeira foi inspecionada.

Aceite manual pendente: o usuário deve abrir o menu e confirmar visualmente os sete atalhos.

## Referências técnicas

- [NotificationListenerService — Android Developers](https://developer.android.com/reference/android/service/notification/NotificationListenerService)
- [Migração para Kotlin integrado — Android Developers](https://developer.android.com/build/migrate-to-built-in-kotlin)
- [Android Gradle Plugin 9.4 — Android Developers](https://developer.android.com/build/releases/agp-9-4-0-release-notes)
