# ADR 0002: Notificações são observações provisórias

- Estado: aceito
- Data: 2026-10-03

## Contexto

Uma notificação depende do app bancário, das configurações do aparelho e da rede. Ela pode atrasar, não aparecer, ser agrupada ou ter texto incompleto. Não deve ser somada como se fosse um lançamento oficial.

## Decisão

Na futura versão, separar evento observado de transação conciliada. Totais definitivos vêm de um extrato oficial ou uma fonte autorizada e apropriada. Uma notificação pode iniciar uma pergunta e receber classificação provisória.

## Consequências

- Um evento sem notificação pode aparecer após a importação do extrato.
- O sistema precisa lidar com duplicidades e transações pendentes.
- Não afirmar reconciliação precisa enquanto a importação ainda não existir.
