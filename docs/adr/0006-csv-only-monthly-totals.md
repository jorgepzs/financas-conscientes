# ADR 0006 — Totais mensais derivados somente do CSV

## Status

Aceita em 3 de outubro de 2026; substituída para a V5.1 pela [ADR 0008](0008-receipt-evidence-and-conservative-reconciliation.md).

## Contexto

Registros manuais e comprovantes compartilhados são úteis para capturar intenção perto do gasto, mas podem conter valor incorreto, ser cancelados, aparecer duplicados ou nunca corresponder a uma movimentação concluída. O painel não pode transformar esse sinal comportamental em fato contábil.

## Decisão

O painel mensal soma apenas transações com direção de saída importadas do CSV oficial. Para categoria e planejado/impulso, usa-se a classificação ligada pelo fingerprint, mas o valor continua sendo o da transação importada. Registros sem fingerprint são marcados como provisórios e não entram em total, orçamento ou categoria.

Uma classificação pode ser excluída sem remover a transação. Nesse caso, a saída volta à fila de pendências. O orçamento é um valor recorrente local e opcional; ele é comparado ao total confirmado do mês.

## Consequências

- O painel permanece reconciliável com o extrato.
- Captura imediata continua possível sem fingir certeza financeira.
- Um gasto recém-registrado pode não aparecer no total até a próxima importação.
- A automação futura deve melhorar a chegada do dado oficial ou produzir apenas um sinal provisório; não deve enfraquecer esta separação.

Esta decisão descreve o comportamento até a V5.0. A ADR 0008 mantém o CSV como referência contábil, mas permite que comprovantes válidos e cifrados apareçam nos totais antes da importação do extrato, com conciliação conservadora e explícita.
