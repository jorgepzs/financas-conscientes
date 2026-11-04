# Interface profissional — V4

## Objetivo

A interface deve reduzir esforço e julgamento. O fluxo principal é comportamental: capturar contexto perto do gasto. O fluxo do CSV é contábil: confirmar valores depois. A tela comunica essa separação sem exigir que o usuário conheça termos técnicos.

## Hierarquia

1. **Captura rápida:** cartão destacado para compartilhar um comprovante ou registrar sem comprovante.
2. **Fonte de verdade:** importação e gerenciamento do extrato em um cartão secundário.
3. **Situação dos dados:** resumo curto de importações, classificações e pendências.
4. **Visão mensal:** total confirmado em cartão de alto contraste, orçamento e decisões.
5. **Pendências e registros:** itens operacionais com estado e ações claras.

O menu de três pontos no cabeçalho funciona como atalho global para comprovantes, registro manual, importação, gerenciamento do extrato, orçamento, privacidade e exclusão total. A tela mantém as ações principais visíveis para não depender do menu.

## Sistema visual

- Fundo neutro claro e superfícies brancas.
- Azul como ação primária e cartão mensal em gradiente escuro.
- Verde para confirmado/planejado, âmbar para provisório/impulso e vermelho somente para ações destrutivas.
- Cartões com raio amplo, borda leve e espaçamento de 8 pontos.
- Botões com altura mínima de 50 dp e distinção entre ação primária, secundária e silenciosa.
- Textos secundários com contraste moderado; valores e títulos formam a hierarquia principal.

## Linguagem

- “Comprovante agora, CSV depois” em vez de explicar arquitetura.
- “Sem julgamento” no campo planejado/impulso.
- Estados curtos: **CONFIRMADO**, **PROVISÓRIO**, **PENDENTE** e **EM DIA**.
- Plurais naturais; evitar textos como “movimentação(ões)”.

## Critérios de aceite visual

- A ação para registrar pelo comprovante é percebida antes do painel.
- Valor mensal e orçamento são identificáveis em poucos segundos.
- Confirmado, provisório e pendente não dependem apenas da cor: todos possuem texto.
- Nenhuma ação destrutiva usa o mesmo estilo da ação principal.
- O formulário exibe rótulos persistentes, exemplos e explicação curta sobre conciliação.
- A interface permanece utilizável com fonte ampliada e sem corte nos botões principais.
