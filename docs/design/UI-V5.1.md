# Interface V5.1 — revisão e anexos locais

## Direção visual

A V5.1 preserva a paleta escura do preview da 0.5.1: fundo `#171B25`, superfície `#202633`, texto principal `#F2F4FA`, texto secundário `#AEB7C8` e primário `#7891FF`. Verde marca estados informativos positivos, âmbar indica revisão e vermelho fica reservado a ações destrutivas. Diálogos compartilham cartão de superfície arredondado, largura confortável, conteúdo rolável e botões legíveis. O formulário de registro abre como sheet inferior; menu de utilidades é um painel tratado pela esquerda, organizado por captura, extrato, planejamento, privacidade e dados locais.

## Formulário e confirmação

- Campos em ordem: valor, o quê, onde/para quem, categoria, decisão e arquivo anexado pelo usuário.
- Seletores de categoria e decisão usam texto claro e fundo escuro tanto no estado fechado quanto nas opções.
- Ações de anexar/substituir, reabrir e remover ficam agrupadas no cartão de anexo; apagar é visualmente destrutivo.
- CSV com correspondências possíveis abre seletor rolável. A pessoa escolhe um registro, ou explicitamente “Nenhum destes — classificar como novo gasto”; essa alternativa existe até quando só há um candidato.
- Importação, exclusões, orçamento, privacidade e erros compartilham o tratamento visual; mensagens explicam a consequência e não ocultam o botão de cancelar.

## Semântica exposta na tela

Os rótulos distinguem `Sem comprovação`, `Anexo do usuário`, `Extrato CSV` e `Anexo + CSV`. “Anexo do usuário” significa apenas arquivo PDF/imagem com formato reconhecido e blob local íntegro: o app não verifica origem, conteúdo, emissor, valor real ou se representa um Pix. O CSV é a fonte dos valores importados. Um arquivo de usuário não é automaticamente classificado como transação.

Os totais incluem saídas CSV e registros com anexos íntegros. A possível conciliação que suprime contagem dupla é limitada a saída CSV ainda pendente, de mesmo valor e data reconhecida em até 48 horas. Sugestões por valor antigo aparecem somente para revisão manual; o painel não reduz totais por elas.

## Configurações experimentais 0.6.1

A tela **Configurações** abre pelo menu lateral, sem ocupar uma das três abas principais. Categorias exibem amostra da cor, quantidade de registros associados e ações separadas de cor, edição e remoção; decisões exibem os mesmos vínculos sem controle de cor. A paleta tem 12 cores nomeadas e os indicadores de distribuição em Resultados usam o mesmo tom da categoria.

Ao remover uma opção em uso, uma confirmação explica a quantidade afetada e a reclassificação: categoria removida torna-se **Sem categoria**; decisão removida torna-se **Não informado**. Valores, comprovantes e vínculos ao extrato são preservados. As opções de fallback são protegidas contra renomeação/remoção.

## Validação visual no aparelho Android

Revisar o menu lateral, formulário em sheet, seletores abertos, diálogos de orçamento, privacidade, importação/erro e exclusão em escala de fonte normal e ampliada. Conferir rolagem com teclado visível, contraste de chips/ações, PDF multipágina e imagens grandes suportadas. Capturas de tela permanecem permitidas por decisão do usuário.
