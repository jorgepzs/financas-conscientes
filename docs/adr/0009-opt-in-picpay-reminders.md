# ADR 0009: Lembretes opt-in por abertura do PicPay

- Status: aceito para a versão experimental 0.6.0
- Data: 2026-10-03

## Contexto

O PicPay não entrega ao app uma notificação confiável de todo Pix enviado. O monitoramento de uso pode perceber a abertura do pacote, mas isso não prova que ocorreu uma transação.

## Decisão

Adicionar um recurso local e opcional usando eventos de `UsageStatsManager` para o pacote exato `com.picpay`, com serviço em primeiro plano, permissão de notificações e acesso ao uso ativados pelo usuário. Abertura gera um lembrete; saída aguarda seis segundos; retorno cancela o encerramento pendente; comprovante válido recebido por `ACTION_SEND` suprime o lembrete final. Sessões sem comprovante recebem as ações “Abrir PicPay”, “Registrar sem comprovante” e “Não fiz Pix”.

O sinal de comprovante é produzido somente depois de `ACTION_SEND` copiar e cifrar PDF/imagem com formato conhecido. CSV, texto, nome de arquivo e falha não contam. A sessão não cria registros financeiros. CSV continua sendo a fonte das movimentações importadas.

## Consequências

- Há uma notificação persistente e maior consumo que um monitor periódico; a pessoa pode desligar e apagar estado local.
- A precisão e continuidade precisam ser validadas em aparelho Android, inclusive políticas de bateria do fabricante.
- É uma funcionalidade de lembrete, não um detector automático de Pix nem prova de transação.
- Não usar Accessibility, leitura de notificações, captura de tela, rede, credenciais ou dados de outras aplicações.
- `specialUse` atende ao monitoramento visível neste build local de teste; publicar em loja exigiria avaliação específica de política.
