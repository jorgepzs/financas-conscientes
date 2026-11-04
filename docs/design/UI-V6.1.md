# Interface V6.1 — app-v2

Aplicada sobre a versão em desenvolvimento `0.6.1-experimental`. Esta revisão troca a apresentação escura anterior por uma interface clara, mantém os fluxos financeiros existentes e melhora a hierarquia das ações e dos resultados.

## Identidade visual

- Fundo verde acinzentado muito claro, cartões brancos e texto verde profundo.
- Verde floresta para ações principais, seleção da navegação e indicadores positivos; âmbar e vermelho ficam reservados a avisos e ações destrutivas.
- Cartões com cantos de 20 dp, campos com 12 dp e áreas de toque de pelo menos 44–50 dp.
- Ícones vetoriais na navegação inferior, logo no cabeçalho e nas telas de comprovante.
- Aparência selecionável entre claro, escuro e automático; a preferência vale também no visualizador de comprovantes e persiste entre aberturas.
- O ícone quadrado Android é definido em `app-v2/src/main/res/drawable/ic_app.xml`; o logo horizontal editável está em `logo-financas-conscientes.svg`.

## Hierarquia e usabilidade

- **Início:** registro rápido no topo, importação do extrato e estado dos dados locais logo abaixo. Pendências incluem um atalho para revisão.
- **Resultados:** total confirmado e limite mensal primeiro; categorias, decisões e maiores gastos em seguida; detalhes de qualidade do extrato ficam no final.
- **Registros:** pendências ficam antes dos registros classificados, com descrições mais curtas e acesso direto à revisão.
- **Formulários:** valor vem primeiro, seguido pelo que aconteceu, onde, categoria e decisão; exemplos aparecem dentro dos campos.
- **Comprovantes:** identidade visual do app, estado de carregamento legível e navegação entre páginas do PDF com alvos amplos.
- **Configurações e utilidades:** usam a mesma paleta, cartões e botões, preservando privacidade, gestão de categorias/decisões e lembretes.

Os números continuam derivados das movimentações importadas e comprovantes válidos conforme as regras existentes. A revisão visual não altera importação, conciliação, armazenamento cifrado nem permissões.

## Tema escuro

A opção **Aparência** no menu e em Configurações oferece **Seguir o sistema**, **Claro** e **Escuro**. O padrão continua claro para preservar a aparência já conhecida; a escolha fica em uma preferência local de interface, separada do arquivo financeiro cifrado. O escuro se inspira em padrões de apps de finanças como o Mobills: fundo grafite, superfícies discretamente elevadas e violeta como acento, sem importar o layout ou a marca. Ciano marca estados positivos; âmbar/vermelho ficam reservados a avisos e ações destrutivas. O resumo mensal recebe um gradiente ameixa. A troca recria a tela imediatamente sem alterar registros, anexos, permissões ou a configuração dos lembretes.
