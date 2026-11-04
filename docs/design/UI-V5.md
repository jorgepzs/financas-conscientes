# Navegação e tela de resultados — V5

> Esta especificação registra a V5.0. Regras visuais e semântica de evidências para V5.1 estão em [UI-V5.1.md](UI-V5.1.md); a revisão 0.5.2 permite anexos cifrados e sua inclusão conservadora nos resultados.

## Decisão

A navegação principal usa três abas inferiores: **Início**, **Resultados** e **Registros**. Ações frequentes permanecem a um toque; tarefas ocasionais ficam no botão de utilidades à esquerda do cabeçalho.

O modelo foi escolhido após comparar um menu lateral completo com abas inferiores. Resultados e registros são parte do uso recorrente e não devem ficar escondidos em um menu.

## Responsabilidade das telas

- **Início:** captura rápida por comprovante, registro manual, importação do CSV e situação dos dados.
- **Resultados:** mês selecionado, saídas confirmadas, orçamento, planejado versus impulso, categorias, maiores gastos e qualidade da importação.
- **Registros:** pendências do extrato, classificações confirmadas ou provisórias e ações de edição/exclusão.
- **Utilidades:** comprovante, registro manual, importação, gerenciamento do extrato, orçamento, privacidade e exclusão total.

## Regras de dados

- Valores do painel vêm apenas das saídas do CSV importado.
- Comprovantes e registros manuais permanecem provisórios até a conciliação.
- A qualidade informa saídas sem contexto, direção desconhecida, data/valor não reconhecidos e última importação.
- Os cinco maiores gastos do mês usam a descrição classificada quando disponível e sinalizam itens pendentes.
- Categorias personalizadas aparecem no mesmo fluxo das opções iniciais, sem tratamento visual estigmatizante.

## Privacidade visual

Capturas de tela são permitidas por decisão do usuário. A versão 0.5.0 não aplica `FLAG_SECURE`. Isso não altera o armazenamento: estado cifrado, sem backup, sem Internet e sem persistir o arquivo bruto do comprovante ou do CSV.

## Paleta do preview na 0.5.1

O tema escuro do preview passou para a interface nativa: fundo `#171B25`, cartões `#202633`, texto `#F2F4FA`, azul primário `#7891FF` e bordas `#333B4D`. Os cartões de situação usam verde `#59C9AB` e âmbar `#FFC46B`. Botões e barras do sistema seguem o mesmo contraste. A 0.5.1 mantém capturas de tela permitidas.

## Critérios de aceite

- A aba selecionada e o mês sobrevivem à recriação da Activity.
- Resultados e registros ficam acessíveis com um toque.
- O menu começa no lado esquerdo e não duplica a navegação principal.
- Telas vazias explicam o próximo passo sem inventar números.
- Pendências e provisórios não são somados como gastos confirmados.
- A interface continua legível em telas Android compactas e aceita capturas de tela.
