# Registro de pesquisa

## 2026-10-03 — Escopo do primeiro experimento

**Pergunta:** O conteúdo das notificações do PicPay em aparelho Android de teste é suficiente para identificar tipo de operação, valor e contraparte?

**Método:** app Android com `NotificationListenerService`, concedido manualmente pelo usuário e filtrado pelo pacote `com.picpay`. Observar operações de teste ou operações normais escolhidas pelo usuário; não coletar de outros apps.

**Resultado:** o ambiente Android foi configurado, o APK foi compilado e instalado em um aparelho de teste por ADB sem fio. O listener foi autorizado. O PicPay publicou notificação para Pix recebido, mas não para Pix enviado. Como os canais do PicPay estavam habilitados, a hipótese de cobertura das saídas foi rejeitada.

**Registro:** anotar somente conclusão e campos presentes/ausentes. Não adicionar ao repositório valores, nomes de contrapartes, identificadores, capturas de tela ou texto de notificações reais.

## Critérios de avaliação

Para cada tipo de movimentação, marcar se a notificação mostra: tipo, valor, nome do favorecido/estabelecimento, status, identificador ou horário. Não inferir valor/contraparte pela aparência do alerta. Distinguir notificações emitidas imediatamente, agrupadas, repetidas ou ausentes.

## Modelo de observação para o dispositivo

| Tipo de evento | Tipo visível? | Valor visível? | Contraparte visível? | Atraso/duplicação/ausência | Observação sem dado pessoal |
|---|---|---|---|---|---|
| Pix enviado | a observar | a observar | a observar | a observar | |
| Pix recebido | a observar | a observar | a observar | a observar | |
| Cartão/débito | a observar | a observar | a observar | a observar | |
| Saque/retirada | a observar | a observar | a observar | a observar | |

## Limitações do método

O diagnóstico mede somente notificações observadas depois da instalação e concessão da permissão. Notificação não equivale a transação confirmada. Para total financeiro, será necessária reconciliação posterior com extrato oficial ou fonte bancária apropriada.

## 2026-10-03 — Fonte oficial disponível

A documentação do PicPay confirma que o extrato contém Pix enviados e recebidos e que pode ser gerado em PDF ou CSV. O CSV passa a ser o formato candidato para importação e reconciliação local. O comprovante de cada movimentação pode ser compartilhado e será avaliado como gesto de captura imediata.
