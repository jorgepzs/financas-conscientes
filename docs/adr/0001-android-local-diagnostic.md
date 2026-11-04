# ADR 0001: Diagnóstico Android local

- Estado: aceito para o MVP
- Data: 2026-10-03

## Contexto

O primeiro risco técnico é saber se o PicPay publica notificações com conteúdo que permita uma pergunta imediata. Construir backend ou automações antes dessa verificação aumenta custo e superfície de dados sem reduzir incerteza.

## Decisão

Criar um aplicativo Android nativo em Kotlin com `NotificationListenerService`, acesso concedido manualmente, filtro estrito do pacote `com.picpay`, histórico local cifrado e ação de exclusão. O MVP não terá permissão de Internet.

## Consequências

- O experimento valida diretamente o comportamento do PicPay no aparelho real.
- Histórico de diagnóstico pode sobreviver ao encerramento do processo sem ficar em texto claro no disco.
- O usuário precisa aprovar o acesso especial nas configurações do Android.
- A ausência de rede impede sincronização remota nesta fase.
- O fluxo não confirma a totalidade nem a liquidação de operações.
