# Apps Android — diagnóstico e aplicação local

O projeto Gradle contém `app`, diagnóstico local de notificações 0.1, e `app-v2`, aplicativo principal em desenvolvimento (0.6.5-experimental; base funcional 0.5.2/V5.1). O app aceita capturas de tela e não declara permissão de Internet. A versão experimental inclui lembretes locais opt-in do PicPay, entrada monetária no estilo bancário, configuração cifrada de categorias/decisões, resumo mensal dinâmico por decisão e temas claro, escuro ou automático. A direção visual está documentada em [`../../docs/design/UI-V6.1.md`](../../docs/design/UI-V6.1.md); o logo horizontal está em [`../../docs/design/logo-financas-conscientes.svg`](../../docs/design/logo-financas-conscientes.svg).

## Funcionalidades implementadas

- Tela mostra se o acesso especial de notificações está ligado e abre as configurações do Android.
- `NotificationListenerService` testa o nome do pacote antes de ler os campos da notificação.
- Captura apenas título e um campo textual de conteúdo (BIG_TEXT, TEXT ou SUB_TEXT), truncados a 600 caracteres.
- Cria identificador local e usa a hora de publicação do Android; não lê intent, imagem, extras genéricos nem dados de outros pacotes.
- Guarda até 100 eventos dos últimos 30 dias em arquivo autenticado/cifrado AES-GCM, com chave não exportável do Android Keystore.
- A tela lista os registros e permite apagá-los. A captura de tela e o preview da tarefa são permitidos.
- Sem permissão `INTERNET`, SDK de analytics ou acesso a credenciais.

## Compilação

Veja [`../../docs/operations/ANDROID.md`](../../docs/operations/ANDROID.md). Requer Android Studio compatível com AGP 9.4.1, Gradle 9.6.0, JDK 17, Android SDK Platform 37 e Build Tools 36.0.0. Kotlin é fornecido pelo recurso integrado do AGP 9; não aplique `org.jetbrains.kotlin.android` neste módulo.

```powershell
.\gradlew.bat :app-v2:lintDebug :app-v2:testDebugUnitTest :app-v2:assembleDebug
```

O APK da aplicação principal aparece em `app-v2/build/outputs/apk/debug/app-v2-debug.apk`.

## Fonte importante

- `app/src/main/java/br/com/financasconscientes/diagnostic/MainActivity.kt`: estado da permissão, lista e exclusão.
- `app/src/main/java/br/com/financasconscientes/diagnostic/PicPayNotificationListener.kt`: entrada de notificações e filtro exato.
- `app/src/main/java/br/com/financasconscientes/diagnostic/data/EncryptedEventStore.kt`: cifragem e retenção.
- `app/src/main/AndroidManifest.xml`: listener protegido pela permissão de sistema.
- `app-v2/src/main/java/br/com/financasconscientes/MainActivity.kt`: telas Início, Resultados e Registros, menu lateral de utilidades, registro, compartilhamento, importação e revisão.
- `app-v2/src/main/java/br/com/financasconscientes/csv/PicPayCsvParser.kt`: parser conservador do CSV PicPay.
- `app-v2/src/main/java/br/com/financasconscientes/dashboard/MonthlyDashboard.kt`: totais, categorias, decisões e maiores gastos derivados do extrato.
- `app-v2/src/main/java/br/com/financasconscientes/dashboard/ReceiptReconciliation.kt`: sugestões de conciliação; candidatos históricos são apenas para revisão e não afetam os totais.
- `app-v2/src/main/java/br/com/financasconscientes/dashboard/StatementDataQuality.kt`: pendências e consistência derivadas das movimentações importadas.
- `app-v2/src/main/java/br/com/financasconscientes/navigation/PrimaryDestination.kt`: destinos e restauração da aba selecionada.
- `app-v2/src/main/java/br/com/financasconscientes/model/ExpenseCategories.kt`: categorias padrão usadas pelos seletores; opções adicionais são configuráveis.
- `app-v2/src/main/java/br/com/financasconscientes/data/EncryptedFinanceStore.kt`: estado financeiro local cifrado.
- `app-v2/src/main/java/br/com/financasconscientes/data/EncryptedReceiptAttachmentStore.kt`: anexos escolhidos pelo usuário, cifrados e limitados a 10 MB.
- `app-v2/src/main/java/br/com/financasconscientes/data/FinanceStateMutations.kt`: exclusão recuperável de movimentações importadas.
- `app-v2/src/main/AndroidManifest.xml`: destino de compartilhamento sem permissões declaradas.

## Limites conhecidos

- A recepção depende de permissão especial concedida pelo usuário.
- O PicPay determina quais notificações publica e qual texto fica visível.
- Reiniciar o aparelho, configurações de bateria e restrições do fabricante podem influenciar a entrega.
- O CSV é a referência das movimentações importadas; anexo PDF/PNG/JPEG/WebP íntegro pode entrar nos totais antes do extrato, sem ser interpretado automaticamente como Pix.
- Registros 0.5.1 sem arquivo cifrado persistido não ganham evidência de anexo retroativamente.
- A visualização de PDF cria uma cópia plaintext transitória no cache privado; fechamento/erro tenta removê-la e a próxima abertura varre resíduos do visualizador após encerramento abrupto.
- A versão 0.5.1 foi instalada como atualização sobre a 0.5.0, mantendo o arquivo cifrado byte a byte idêntico. A revisão visual no aparelho seguiu em andamento.
