# Fonte de verdade financeira

## Definição para o projeto

O app tem dois conceitos diferentes:

| Dado | O que representa | Uso permitido |
|---|---|---|
| Evento de notificação | O Android informou que um app publicou um alerta | Disparar uma pergunta ou iniciar investigação |
| Transação de extrato | O registro entregue pelo banco em um extrato oficial | Cálculos financeiros, totais e reconciliação |

O MVP de diagnóstico implementa somente o primeiro conceito. Seu histórico não é relatório financeiro oficial. A validação no aparelho mostrou que Pix enviados podem não gerar notificação.

## Por que não usar a notificação para totalizar

- O PicPay pode não emitir notificação para cada movimentação.
- O Android pode agrupar, ocultar ou entregar em outro momento.
- O título ou texto pode mudar com idioma, versão do app ou configuração de privacidade.
- A notificação pode ser atualizada e aparecer mais de uma vez.
- O alerta informa publicação, não necessariamente confirmação final ou liquidação.

## Política futura de reconciliação

1. Preservar captura rápida e classificação como dados provisórios.
2. Importar o CSV oficial gerado pelo PicPay.
3. Comparar data, valor, tipo e contraparte somente quando os campos existirem.
4. Se houver mais de uma correspondência, pedir revisão humana; não escolher silenciosamente.
5. Usar valores do extrato para totais confirmados.
6. Mostrar notificações sem correspondência e movimentações de extrato sem resposta como pendências separadas.

O PicPay documenta exportação em PDF e CSV, incluindo detalhes como tipo de movimentação, origem/destino e forma de pagamento. O layout exato do CSV ainda precisa ser verificado numa amostra anonimizada da conta real antes de implementar o parser.

O PicPay informa que o arquivo também é enviado ao e-mail cadastrado em até 30 minutos. Nesta fase, o projeto usará compartilhamento/importação explícitos; não terá acesso ao e-mail do usuário.

Referências oficiais: [conteúdo do extrato](https://meajuda.picpay.com/hc/pt-br/articles/360055117852-Como-acessar-o-Extrato-do-PicPay) e [exportação PDF/CSV](https://meajuda.picpay.com/hc/pt-br/articles/4408499088525-Como-baixar-o-extrato-em-PDF).
