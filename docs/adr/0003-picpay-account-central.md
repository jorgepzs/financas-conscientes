# ADR 0003: Manter a conta PicPay central atual

- Estado: aceito conforme preferência do usuário
- Data: 2026-10-03

## Contexto

O usuário informou que hoje centraliza tudo na mesma conta e quer manter esse arranjo.

## Decisão

Não exigir abertura de outra conta ou mudança do fluxo bancário para usar a solução. O software deve observar os limites de dados que o PicPay oferece e admitir importação futura do extrato como possível fonte de confirmação.

## Consequências

- O MVP não modifica conta, limite, saldo nem regras de pagamento.
- Qualquer fonte de verdade futura deve ser confirmada tecnicamente e documentada antes de apresentar totais definitivos.
