# Segurança e privacidade

## Dados que podem aparecer

Título e texto de uma notificação financeira podem revelar valores, nomes e hábitos de consumo. Trate todo conteúdo capturado como confidencial, inclusive durante diagnóstico.

## Controles no MVP

- Filtro exato de `packageName == "com.picpay"` antes de acessar texto.
- Somente `EXTRA_TITLE`, `EXTRA_TEXT` e `EXTRA_BIG_TEXT`; nenhum dump de `Bundle`.
- Cada campo de texto limitado a 600 caracteres.
- Histórico limitado a 100 itens, descartando registros com mais de 30 dias.
- Dados cifrados em arquivo no armazenamento interno privado, com AES/GCM/NoPadding e chave criada no Android Keystore.
- O app desativa backup (`allowBackup=false`, `fullBackupContent=false`).
- A ação “Apagar histórico” remove o arquivo local.
- Nenhuma permissão de Internet, serviço de analytics ou SDK publicitário.
- Sem qualquer campo de senha ou token.

## Riscos residuais

- Um usuário com aparelho desbloqueado pode ler os eventos dentro do próprio app.
- Quem controla o aparelho pode habilitar o acesso do listener e ver o app na tela de acesso concedido.
- Root, sistema comprometido ou depuração privilegiada podem contornar proteções locais.
- Chave perdida/corrompida torna os dados cifrados irrecuperáveis; o app informa falha, e o usuário pode limpar o histórico.
- Conteúdo sensível pode permanecer na tela ou em uma captura de tela feita pelo próprio usuário; o Android e o usuário controlam isso.

## Tratamento de dados para desenvolvimento

- Não versionar extratos, dumps, conteúdo de notificação ou prints do banco.
- Ao relatar resultados, registrar apenas quais categorias de campo estão presentes/ausentes.
- Evitar incluir eventos reais em issues, logs de build ou mensagens compartilhadas.
- Limpar histórico no fim do experimento, se não houver razão para mantê-lo.

## Procedimento em caso de exposição

1. Revogar o acesso do listener nas configurações do Android.
2. Apagar o histórico dentro do app ou remover os dados do aplicativo nas configurações do sistema.
3. Se alguma informação saiu do aparelho, identificar local e destinatários da exposição, remover cópias controláveis e revisar a causa antes de retomar.

## Revisão antes de uma futura sincronização

Qualquer servidor deve exigir autenticação forte, TLS, segredo fora do Git, autorização revogável, logs sem conteúdo financeiro, política de retenção e mecanismo testável de exclusão. A sincronização não está implementada e não faz parte do MVP.

## Anexos da aplicação principal V5.1 / 0.5.2

- O usuário escolhe/compartilha o arquivo; são aceitos PDF, PNG, JPEG e WebP até 10 MB. O conteúdo é transferido em fluxo para armazenamento `noBackupFilesDir`, cifrado com AES-GCM usando chave do Android Keystore. Nome original, URI, texto compartilhado e origem não são persistidos.
- Assinatura de formato e tag GCM confirmam tipo reconhecido e integridade local da cópia; não confirmam emissor PicPay, validade do comprovante, valor, estorno ou que a operação seja Pix.
- Verificação GCM é executada em worker antes de classificar anexo como íntegro nos resultados. Na inicialização, blobs UUID não referenciados só são limpos depois que o estado cifrado é carregado com sucesso.
- O visualizador de imagens mantém bytes descriptografados em memória. Para PDF, `PdfRenderer` precisa de arquivo seekable, então o app cria cópia plaintext transitória apenas no cache privado, tenta excluí-la no fechamento/erro e remove arquivos próprios `receipt-preview-*.pdf` na próxima abertura. Um encerramento abrupto pode deixá-la no cache até essa abertura; não há cópia compartilhada com outros apps. PDF não confiável ainda é tratado no mesmo processo — isolamento em processo separado permanece como melhoria futura.
- A exclusão do registro remove seu blob cifrado. A ação para apagar todos os dados remove estado, blobs e chaves. Capturas de tela não são bloqueadas.

## Lembretes locais experimentais V0.6

- O acesso a Usage Stats e às notificações é opt-in e pode ser revogado nas Configurações do Android; o serviço em primeiro plano mostra uma notificação persistente.
- O monitor compara `UsageEvents.packageName` em memória com o pacote exato `com.picpay`. Nenhum nome de pacote, classe de tela ou evento de outro app é escrito em disco ou log.
- A persistência é limitada ao opt-in, UUID/estado da sessão atual, timestamps, flags de lembrete/recebimento e cursor da consulta. Não coleta texto/valor de transação, notificações, credenciais, telas ou conteúdo de outras aplicações.
- Recebimento para fins do lembrete exige que o compartilhamento `ACTION_SEND` termine a cópia cifrada de um PDF/imagem com formato reconhecido. Essa verificação não prova emissor, conteúdo, valor ou ocorrência de Pix.
- Nenhum estado de lembrete é conectado ao modelo de despesas. Não há sincronização nem permissão de Internet.
- O monitor consome bateria e a continuidade depende da política de segundo plano do Android e do fabricante. Não interpretar ausência de notificação como ausência de abertura, nem lembrete como evidência de pagamento.
