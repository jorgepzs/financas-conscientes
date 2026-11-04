# Esquema CSV do PicPay observado

Data da observação: 3 de outubro de 2026.

Uma amostra real fornecida pelo proprietário foi processada localmente para confirmar somente estrutura e formatos. O arquivo e suas movimentações não foram copiados para o projeto.

## Formato

- Codificação: UTF-8 com BOM.
- Separador: vírgula.
- Cabeçalho, na ordem observada: `data`, `hora`, `tipo`, `origem / destino`, `valor`, `forma de pagamento`.
- Data: `yyyy-MM-dd`.
- Hora: `HH:mm`.
- Valor: moeda brasileira com `R$`, separador decimal por vírgula e agrupamento opcional por ponto.
- Entrada: sinal `+`.
- Saída: sinal Unicode `−` (`U+2212`), diferente do hífen ASCII.
- Forma de pagamento observada: saldo ou cartão; o campo pode estar vazio.
- Não foi observado identificador único de transação.

Exemplo sintético, sem dados reais:

```csv
data,hora,tipo,"origem / destino",valor,"forma de pagamento"
2026-10-02,08:15,Pix enviado,PESSOA A,"−R$ 12,34",Com saldo
2026-10-02,09:20,Pix recebido,PESSOA B,"+R$ 1.234,56",
```

## Consequências para o parser

- O sinal Unicode é normalizado antes da conversão do valor.
- Data e hora são combinadas antes da reconciliação temporal.
- A direção usa prioritariamente o sinal; o tipo serve como apoio.
- Como não há ID, o fingerprint usa todos os campos normalizados e o índice de ocorrência de linhas idênticas. Assim, duas operações realmente idênticas no mesmo minuto são preservadas, e uma nova importação do mesmo arquivo não as duplica.
- O parser recusa arquivos sem colunas reconhecíveis de data e valor.
- Limites atuais: 5 MB e 20.000 linhas por importação.

O formato pode mudar em versões futuras do PicPay. Uma falha de reconhecimento deve bloquear a importação e pedir revisão, nunca reinterpretar colunas silenciosamente.
