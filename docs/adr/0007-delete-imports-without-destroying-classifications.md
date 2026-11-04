# ADR 0007 — Excluir importações sem destruir classificações

## Status

Aceita em 3 de outubro de 2026.

## Contexto

O usuário precisa corrigir importações erradas ou remover períodos do extrato local. Uma transação pode já ter recebido descrição, categoria e decisão planejado/impulso. Apagar silenciosamente esse trabalho junto com a linha importada seria surpreendente e difícil de recuperar.

## Decisão

Excluir uma transação importada remove somente a transação e seu fingerprint do estado. Uma classificação ligada permanece com valor e metadados, mas perde o fingerprint e passa a ser provisória. Excluir todo o extrato aplica a mesma regra a todas as classificações. O orçamento não é alterado.

Todas as exclusões exigem confirmação e informam que a reimportação do CSV restaura as movimentações. A exclusão total do aplicativo continua separada e remove estado e chave.

## Consequências

- Erros de importação podem ser corrigidos sem perder classificação manual.
- A operação é recuperável enquanto o usuário possuir o CSV.
- Após reimportar, a conciliação precisa ser revisada novamente; vínculos não são refeitos silenciosamente.
- Totais mensais diminuem imediatamente porque usam apenas transações atualmente importadas.
