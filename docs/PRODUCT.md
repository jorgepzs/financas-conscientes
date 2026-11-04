# Visão do produto

## Problema

Registrar gastos somente no fim do mês depende da memória e dificulta entender o contexto das decisões. Uma captura simples, próxima ao momento do gasto, pode ajudar a pessoa a nomear a finalidade e observar padrões sem julgamento.

## Usuário e contexto inicial

- Aplicativo Android local, com armazenamento privado e sem exigir infraestrutura de servidor.
- O fluxo inicial usa exportações PicPay selecionadas pela própria pessoa.
- Evitar custos recorrentes e reduzir dependências externas.
- Manter decisões técnicas e limites documentados para facilitar manutenção.

Essas condições orientam a primeira versão. Não presumimos que notificações do PicPay contenham todos os dados nem que exista uma API gratuita de produção.

## Resultado do primeiro MVP

O primeiro MVP respondeu à pergunta “O PicPay publica notificações utilizáveis para todas as saídas?”: não. No aparelho observado, Pix recebido gerou evento, mas Pix enviado não gerou notificação.

O app de diagnóstico:

- apresenta o estado do acesso especial a notificações;
- encaminha o usuário à tela de configurações do Android para concedê-lo;
- recebe notificações somente quando o pacote é exatamente `com.picpay`;
- exibe horário, título e texto disponível, com tamanho limitado;
- conserva no máximo 100 eventos recentes por até 30 dias, criptografados pelo Android Keystore;
- permite apagar o histórico;
- não sincroniza nem envia dados para fora do aparelho.

## Aplicação principal atual

- recebe um comprovante compartilhado pelo usuário e abre uma pergunta curta de classificação;
- importa localmente o CSV oficial do PicPay;
- mantém o CSV como referência de saídas importadas e cria pendências para gastos esquecidos;
- guarda anexos escolhidos pelo usuário cifrados localmente; formato/integridade não verificam se o documento é PicPay ou Pix;
- inclui anexos íntegros nos resultados antes do CSV e só evita dupla contagem em possíveis correspondências próximas, sujeitas a revisão;
- apresenta painel por mês, orçamento recorrente, categorias com cores configuráveis e decisões planejado versus impulso;
- permite cadastrar, renomear e remover categorias/decisões sem apagar registros existentes;
- permite corrigir ou excluir classificações individuais;
- mantém tudo no aparelho, sem credenciais bancárias, servidor ou acesso global às notificações.

## Fora do escopo desta fase

- Integração Open Finance, scraping ou automação do internet banking.
- WhatsApp, Telegram, notificações push ou servidor.
- Integração automática Open Finance, classificação por IA ou painel remoto.
- Bloqueio de compras, aconselhamento clínico ou promessas de prevenir gastos.
- Captura de notificações de outros apps.

## Sucesso observável da aplicação principal

1. O mesmo CSV pode ser importado novamente sem duplicar movimentações.
2. Todas as saídas importadas e ainda não classificadas aparecem como pendência.
3. Totais incluem saídas CSV e anexos íntegros escolhidos pelo usuário; manual sem anexo íntegro não soma, e possíveis duplicidades próximas ficam visíveis para revisão.
4. O usuário enxerga quanto foi planejado, quanto foi impulso e quanto segue sem decisão informada.
5. O orçamento mostra valor disponível ou excedido usando o total confirmado.
6. O estado cifrado sobrevive ao fechamento e à atualização do app, sem entrar no backup Android.
7. O usuário pode corrigir classificações e apagar registros ou todo o estado local.

## Hipóteses ainda abertas

- O PicPay instalado no dispositivo usa o nome de pacote `com.picpay`.
- ~~O PicPay apresenta notificações para cada tipo de saída relevante.~~ Hipótese rejeitada para Pix enviado.
- O conteúdo contém dados úteis em texto sem exigir abrir o PicPay.
- As opções de bateria do fabricante podem afetar a continuidade do serviço Android.

Estas hipóteses devem ser confirmadas em dispositivos Android de teste e registradas em `docs/research/DEVICE-OBSERVATIONS.md` sem incluir informações financeiras reais ou identificadores pessoais no repositório.
