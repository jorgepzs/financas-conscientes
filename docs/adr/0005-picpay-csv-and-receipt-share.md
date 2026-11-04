# ADR 0005: CSV do PicPay confirma; comprovante compartilhado inicia a pergunta

- Estado: aceito
- Data: 2026-10-03

## Contexto

No teste em aparelho Android, o PicPay publicou notificação para Pix recebido, mas não publicou notificação para Pix enviado. Os canais do pacote `com.picpay` estavam habilitados e nenhum canal específico de saída estava desativado. Portanto, um listener de notificações não consegue iniciar perguntas de modo confiável para gastos.

O PicPay informa oficialmente que seu extrato contém Pix e TED enviados e recebidos e pode ser gerado em PDF ou CSV. Também permite abrir e compartilhar o comprovante de uma movimentação.

## Decisão

Usar três camadas distintas:

1. O usuário compartilha o comprovante do gasto para o app quando quiser registrar imediatamente categoria, finalidade e caráter planejado/impulsivo. O app não precisa conservar a imagem ou o PDF bruto.
2. O CSV oficial do PicPay é importado localmente e prevalece para valor, data, tipo, origem/destino e existência da movimentação.
3. O `NotificationListenerService` permanece somente no APK de diagnóstico. A aplicação principal não solicitará acesso global às notificações, pois a cobertura observada não justifica o risco e o atrito de instalação.

Movimentações de saída presentes no CSV e ainda sem classificação entram numa fila de pendências. A reconciliação não escolhe silenciosamente quando houver mais de uma correspondência possível.

## Consequências

- O registro imediato exige uma ação curta de compartilhar o comprovante; não é automação total.
- O extrato periódico corrige esquecimentos e garante completude financeira.
- Não serão usados acessibilidade, captura de tela, scraping do PicPay ou leitura de notificações do WhatsApp.
- A retirada do listener da aplicação principal reduz a superfície de acesso a dados sensíveis e evita depender de um sinal incompleto.
- A implementação do parser depende de uma amostra anonimizada do CSV real para confirmar cabeçalhos, datas, valores e codificação.

## Fontes

- [Como acessar o Extrato do PicPay](https://meajuda.picpay.com/hc/pt-br/articles/360055117852-Como-acessar-o-Extrato-do-PicPay)
- [Como baixar o extrato em PDF ou CSV](https://meajuda.picpay.com/hc/pt-br/articles/4408499088525-Como-baixar-o-extrato-em-PDF)
- [Como acessar e compartilhar comprovantes](https://meajuda.picpay.com/hc/pt-br/articles/360059089711-Como-acesso-os-comprovantes-no-meu-Extrato-do-PicPay)
