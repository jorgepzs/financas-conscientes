# Detecção automática de Pix enviado pelo PicPay

**Status:** investigação concluída
**Data de corte:** 2026-10-03
**Escopo:** conta PicPay de pessoa física em aparelho Android de teste, sem custo recorrente
**Documentos de base:** ADR 0005, esquema documentado do CSV, política de fonte de verdade, operação V2 e arquitetura atual

> Esta investigação não leu nem reproduziu o CSV financeiro original. Ela usa somente o esquema já documentado em `PICPAY-CSV-SCHEMA.md`.

## Resumo executivo e veredito

**Veredito: automação parcialmente viável.**

É possível criar, gratuitamente e com boa segurança, um gatilho em segundos quando o Pix **começa pelo Finanças Conscientes**: o app registra uma intenção local, abre o PicPay e, ao voltar ao primeiro plano, pergunta se o Pix foi concluído. Isso reduz bastante a dependência de memória, não requer leitura de tela, notificações, credenciais bancárias, servidor ou permissões sensíveis.

Não é possível, nas restrições atuais, detectar automaticamente e com confiabilidade todos os Pix enviados de qualquer lugar do PicPay em segundos ou poucos minutos. Não foi encontrada API oficial pública para ler transações de uma conta pessoal PicPay; as APIs públicas do PicPay são produtos para lojistas e cobranças. O Android também não oferece a um app comum um sinal que diga que outro app concluiu um Pix. O Open Finance contém dados de transações, mas o acesso direto é institucional e certificado; os agregadores gratuitos encontrados não fornecem atualização em tempo real com garantia.

A arquitetura recomendada é, portanto:

1. **gatilho provisório local:** intenção iniciada no Finanças Conscientes + pergunta ao retornar;
2. **caminho complementar:** compartilhamento manual do comprovante, já existente na V2, e um atalho explícito para Pix iniciado fora do app;
3. **fonte de verdade:** importação e reconciliação posterior com o CSV oficial do PicPay.

Essa separação é obrigatória:

- **gatilho provisório** significa “há motivo para perguntar/classificar”; não afirma que houve débito;
- **evidência auxiliar** é a confirmação do usuário ou um comprovante compartilhado; pode ter erro, cancelamento ou duplicidade;
- **fonte de verdade** continua sendo a linha normalizada do CSV oficial.

### Classificação das afirmações

- **Fato confirmado:** consta em documentação oficial ou nos documentos/observações já registrados no projeto.
- **Inferência:** conclusão técnica derivada de fatos, explicitamente marcada.
- **Hipótese a testar:** comportamento dependente da versão do PicPay, da política Android do fabricante ou do uso real, que exige experimento controlado.

## Conclusões por área investigada

### 1. APIs oficiais do PicPay

**Fato confirmado.** A documentação pública para desenvolvedores do PicPay descreve produtos de e-commerce/lojista. A autenticação usa credenciais obtidas no Painel Lojista e `client_credentials`; o webhook de Pix informa mudanças de estado de uma **cobrança** recebida pelo lojista. Isso não é autorização para consultar a movimentação da conta pessoal nem para observar Pix enviados por ela. Fontes: [portal PicPay Developers](https://developers-business.picpay.com/), [autenticação da API Pix](https://developers-business.picpay.com/pix/en/docs/authentication) e [webhook da API Pix](https://developers-business.picpay.com/pix/docs/webhook).

**Fato confirmado sobre a pesquisa.** Até a data de corte, não foi localizado contrato público do PicPay para pessoa física que ofereça listagem ou webhook de transações da conta, nem callback de “Pix enviado” para um app pessoal.

**Inferência.** Adaptar uma API de e-commerce não resolve o problema e confundiria dois papéis opostos: receber uma cobrança como lojista e observar um débito da conta pessoal. A V3 não deve criar credenciais de lojista nem presumir que uma API empresarial acessa a conta PF.

**Hipótese a testar fora da V3.** Uma consulta formal ao suporte/desenvolvedores do PicPay poderia perguntar se existe programa parceiro para acesso a conta PF. Só deve alterar o veredito se houver contrato público, escopo de transações de conta, preços e termos aplicáveis a uso pessoal.

### 2. Open Finance

#### O que ele tecnicamente oferece

**Fato confirmado.** O Open Finance permite, mediante consentimento, compartilhar dados de contas e movimentações com uma instituição receptora. O próprio PicPay documenta que o cliente pode trazer dados de outras instituições ao PicPay ou compartilhar dados do PicPay com outra instituição. Fontes: [FAQ do Banco Central](https://www.bcb.gov.br/meubc/faqs/s/open-finance), [Central de Ajuda do PicPay — Open Finance](https://meajuda.picpay.com/hc/pt-br/categories/4407869759635-Open-Finance) e [API Contas do Open Finance](https://openfinancebrasil.atlassian.net/wiki/spaces/OF/pages/207716736).

**Fato confirmado.** A API de Contas possui recursos de transações, inclusive uma visão de transações correntes. Orientações do ecossistema determinam identificador estável de transação para Pix no domínio da API. Isso é melhor semanticamente do que o CSV atual, cujo esquema documentado não possui ID único. Fonte: [orientações da API de Contas](https://openfinancebrasil.atlassian.net/wiki/spaces/OF/pages/1739096252/Orienta%2Bes%2B-%2BDC%2BContas).

#### Por que o app pessoal não pode acessar diretamente

**Fato confirmado.** Apenas instituições autorizadas a funcionar pelo Banco Central podem participar diretamente do Open Finance. Participantes precisam integrar-se ao diretório, usar certificados e perfis de segurança, passar por testes/homologação e obter certificações obrigatórias. Há custos de certificação de segurança. Fontes: [FAQ do Banco Central](https://www.bcb.gov.br/meubc/faqs/s/open-finance), [padrão de certificados](https://openfinancebrasil.atlassian.net/wiki/spaces/OF/pages/245694518), [diretrizes de certificação](https://openfinancebrasil.atlassian.net/wiki/spaces/OF/pages/280297514/Diretrizes%2BGerais%2Bde%2BCertifica%2Bo%2Bde%2BConformidade) e [guia de certificação](https://openfinancebrasil.atlassian.net/wiki/spaces/OF/pages/155910145/Guia%2Bde%2BCertifica%2Bo%2Bde%2BConformidade).

**Inferência.** O Finanças Conscientes, como aplicativo pessoal não regulado, não pode simplesmente obter um certificado e consultar a API do PicPay. Nem um servidor doméstico nem a Oracle Cloud Free Tier eliminam as exigências institucionais, jurídicas, de diretório e de conformidade.

#### Polling, webhooks e latência

**Fato confirmado.** A tabela oficial de requisitos não funcionais classifica `transactions-current` como endpoint de alta frequência, com SLA de resposta de 1.500 ms e limite operacional indicado de 240 consultas bem-sucedidas por mês por combinação relevante. O SLA é tempo de resposta da API, não garantia de que uma nova transação aparecerá em 1,5 segundo. Fonte: [referência de requisitos não funcionais](https://openfinancebrasil.atlassian.net/wiki/spaces/OF/pages/17957025).

**Inferência.** Polling por minuto exigiria cerca de 43.200 chamadas/mês; por cinco minutos, 8.640. Isso é incompatível com o limite publicado de 240/mês e, mesmo por meio de um participante, não sustenta a promessa de detecção em segundos ou poucos minutos.

**Fato confirmado.** O webhook padronizado encontrado no Open Finance está ligado ao ciclo de consentimento/pagamento **iniciado via API de Pagamentos** e orienta o recebedor a consultar o recurso após o evento. Não é um webhook genérico que avisa toda nova transação surgida na conta. Fonte: [Webhook de Pagamentos](https://openfinancebrasil.atlassian.net/wiki/spaces/OF/pages/1436549347/Pagamentos%2B-%2BWebhook%2B-%2Bv1.3.0).

**Inferência.** Um webhook quase imediato seria possível se o próprio Pix fosse iniciado por um iniciador regulado integrado ao app. Isso muda o produto: exige um iniciador/agregador comercial, contrato e backend, além de ampliar segurança, privacidade e compliance. Não atende à restrição de custo recorrente e não pertence à V3.

#### Agregador

**Fato confirmado segundo o fornecedor.** O Meu Pluggy oferece uso pessoal gratuito e lista conexão PF do PicPay via Open Finance, mas suas conexões gratuitas são atualizadas automaticamente a cada 24 horas; não há atualização manual nem SLA contratual nesse plano. Webhooks apenas refletem as atualizações realizadas. Já o produto comercial de dados é anunciado a partir de R$ 2.500/mês e o de pagamentos a partir de R$ 500/mês na data de corte. Fontes do fornecedor: [preços](https://www.pluggy.ai/precos), [atualização de conexões](https://v2.docs.pluggy.ai/pt/docs/connections/item) e [status dos conectores](https://status.pluggy.ai/).

**Inferência.** O plano gratuito pode, no máximo, ser uma camada diária de conveniência/auditoria. Ele não satisfaz detecção imediata, adiciona um terceiro com acesso consentido aos dados e não substitui o CSV. O preço comercial elimina a opção paga sob a restrição atual.

**Regra de segurança para qualquer experimento futuro.** Prosseguir somente se o conector estiver explicitamente identificado como Open Finance, redirecionar a autenticação/consentimento para canal oficial do PicPay e nunca pedir ao Finanças Conscientes para capturar senha, PIN, token ou biometria. Abandonar imediatamente se o fluxo pedir credenciais bancárias dentro de uma interface de terceiro ou usar “conector direto”/scraping.

### 3. Intents, deep links, App Links e atalhos Android

**Fato confirmado.** Um app Android pode abrir a atividade principal de outro app por `Intent`, sujeito às regras de visibilidade de pacotes. Isso só prova que o destino foi aberto. Para receber um resultado confiável, o app de destino precisa publicar um contrato de intent/deep link/callback correspondente. Fontes: [interação entre apps](https://developer.android.com/training/basics/intents/) e [visibilidade de pacotes](https://developer.android.com/training/package-visibility/declaring).

**Fato confirmado sobre a pesquisa.** Não foi encontrado contrato público do PicPay para iniciar um Pix PF com parâmetros e devolver sucesso/cancelamento ao app chamador. Os links documentados no portal de desenvolvedores são fluxos comerciais específicos, não uma API genérica da conta pessoal.

**Inferência.** A V3 pode abrir somente o launcher oficial do PicPay. Não deve depender de URI privada obtida por inspeção, nome interno de `Activity` ou parâmetros descobertos empiricamente, pois isso seria uma API privada instável.

**Fato confirmado.** App Links e deep links funcionam quando o app receptor declara filtros; links web verificados exigem associação do domínio. Um app chamador não pode inventar suporte no PicPay. Fonte: [criação de deep links](https://developer.android.com/training/app-links/create-deeplinks).

**Fato confirmado.** Um app comum também não pode enumerar/invocar de modo geral os atalhos internos de outro app; essa capacidade é reservada principalmente ao launcher padrão ou assistente. Fonte: [LauncherApps.ShortcutQuery](https://developer.android.com/reference/android/content/pm/LauncherApps.ShortcutQuery.html).

**Recomendação.** Usar um `Intent` apenas para abrir o launcher oficial, com uma declaração `<queries>` restrita ao pacote conhecido — nunca `QUERY_ALL_PACKAGES`. O retorno ao Finanças Conscientes é um evento de ciclo de vida, não um resultado do Pix.

### 4. Fluxo “Fazer Pix” e retorno ao app

**Inferência recomendada.** Registrar a intenção **antes** de abrir o PicPay é o único ponto totalmente controlável, imediato, local e gratuito. Quando o Finanças Conscientes volta ao primeiro plano, a existência dessa intenção permite perguntar “Você concluiu o Pix?”.

O retorno não prova pagamento. As causas possíveis incluem: Pix concluído, cancelado, falha, consulta de saldo, interrupção por ligação, bloqueio de tela ou processo encerrado. Por isso, a resposta “Sim” cria apenas um registro provisório, nunca uma transação confirmada.

**Hipótese a testar.** Em um aparelho Android de teste, o PicPay deve abrir pelo launcher e o app deve receber `onResume` ao voltar. Otimizações agressivas de bateria ou encerramento do processo podem alterar a sequência; a persistência local do estado deve tornar o fluxo recuperável.

### 5. UsageStats

**Fato confirmado.** `UsageStatsManager` e os eventos `ACTIVITY_RESUMED`/`ACTIVITY_PAUSED` podem indicar que atividades de um pacote entraram ou saíram do primeiro plano. Para consultar outros apps, normalmente é necessário declarar `PACKAGE_USAGE_STATS` e o usuário conceder “Acesso ao uso” nas Configurações. Fontes: [UsageStatsManager](https://developer.android.com/reference/android/app/usage/UsageStatsManager) e [UsageEvents.Event](https://developer.android.com/reference/android/app/usage/UsageEvents.Event).

**Inferência.** Esse acesso não informa qual tela foi usada, valor, destinatário, sucesso ou sequer se o usuário entrou no fluxo Pix. Abrir/fechar o PicPay tem cobertura maior que o botão da V3, mas péssima especificidade e gera perguntas falsas ao abrir o app para cartão, saldo, rendimento ou recebimentos.

**Decisão.** Não incluir na V3. O botão “Fazer Pix” já fornece um marcador explícito e mais privado sem permissão especial. UsageStats só mereceria um protótipo separado se a adoção do botão fosse baixa e o usuário aceitasse os falsos positivos; ainda assim seria gatilho, nunca prova.

### 6. Compartilhamento de comprovantes

**Fato confirmado.** O PicPay oferece a ação “Compartilhar comprovante”. O Android encaminha compartilhamentos por `ACTION_SEND`, e o usuário escolhe o destino na folha de compartilhamento. Fontes: [comprovantes no PicPay](https://meajuda.picpay.com/hc/pt-br/articles/48204456769939-Como-acessar-um-comprovante-de-pagamento-no-Assistente-de-Pagamentos) e [recebimento de dados compartilhados no Android](https://developer.android.com/develop/ui/compose/sharing/receive).

**Inferência.** O Finanças Conscientes pode tornar-se um alvo conveniente e processar localmente o comprovante, mas não pode fazer o PicPay compartilhar automaticamente sem cooperação explícita do PicPay ou automação frágil de interface. O gesto do usuário é desejável: limita o acesso ao arquivo escolhido.

**Decisão.** Manter o compartilhamento da V2 como melhor cobertura para Pix iniciado diretamente no PicPay. Melhorias possíveis na V3: instrução curta pós-Pix, destino de compartilhamento claramente nomeado, deduplicação e descarte do arquivo bruto após extração cifrada conforme a política existente.

### 7. E-mail, Assistente PicPay no WhatsApp e arquivos locais

#### E-mail

**Fato confirmado.** O PicPay documenta que um extrato solicitado pelo usuário pode ser baixado/compartilhado e também enviado por e-mail, com prazo de até 30 minutos. Não foi encontrada promessa oficial de um e-mail transacional para cada Pix enviado. Fonte: [extrato da conta PicPay](https://meajuda.picpay.com/hc/pt-br/sections/360011869332-Extrato-de-conta).

**Inferência.** Observar caixa de e-mail por IMAP não dá cobertura garantida, não é um gatilho em segundos e adiciona credencial/token de e-mail, rede, processamento em segundo plano e manutenção. Um anexo de extrato continua sendo uma exportação sob demanda, não evento transacional. Não recomendar.

#### Assistente PicPay no WhatsApp

**Fato confirmado.** O PicPay documenta um fluxo oficial para iniciar Pix pelo WhatsApp; a conclusão e autenticação ocorrem no app PicPay e, após a confirmação, o Assistente envia confirmação e link do comprovante na conversa. Fontes: [transferência Pix pelo WhatsApp](https://meajuda.picpay.com/hc/pt-br/articles/33132134741395-Como-eu-fa%C3%A7o-uma-transfer%C3%AAncia-Pix-pelo-WhatsApp) e [segurança do Pix pelo WhatsApp](https://meajuda.picpay.com/hc/pt-br/articles/33132560768787-%C3%89-seguro-fazer-uma-transfer%C3%AAncia-Pix-pelo-WhatsApp).

**Inferência.** Usar o Assistente não cria uma integração autorizada entre WhatsApp e Finanças Conscientes. Detectar automaticamente a resposta exigiria ler notificações amplas do WhatsApp, Accessibility ou automação de interface, expondo conversas não financeiras. Isso é desproporcional e deve ser rejeitado. O usuário ainda pode compartilhar manualmente o comprovante/link, se o formato for aceito com segurança.

#### Arquivos baixados

**Fato confirmado.** Com armazenamento com escopo, um app não tem acesso irrestrito aos arquivos criados por outros apps na pasta Downloads. Para um arquivo de outro app, o caminho normal é o Storage Access Framework, em que o usuário escolhe explicitamente o documento. O acesso “todos os arquivos” é amplo, reservado a casos centrais como gerenciadores de arquivos e sujeito a restrições. Fontes: [armazenamento compartilhado](https://developer.android.com/training/data-storage/shared/media) e [gerenciamento de todos os arquivos](https://developer.android.com/training/data-storage/manage-all-files).

**Inferência.** Um observador de diretório não pode depender de enxergar silenciosamente comprovantes baixados pelo PicPay. A alternativa segura é “Importar comprovante”, com seletor do sistema, ou receber `ACTION_SEND`. Não solicitar `MANAGE_EXTERNAL_STORAGE`.

### 8. Trocar a conta central

**Inferência.** Trocar de instituição pode ser racional se o requisito principal for cobertura automática de todos os Pix, mas só há forte justificativa quando a nova conta oferece um contrato público verificável: webhook/API PF ou, no mínimo, notificação de saída consistente somada a bom extrato. Notificação continuaria sendo gatilho, não fonte contábil.

Não foi selecionada uma instituição específica nesta pesquisa porque promessas de aplicativos e APIs mudam, APIs públicas em geral atendem PJ/lojistas, e recomendar migração sem validar tarifa, segurança, exportação e cobertura criaria um custo de troca alto.

Critérios para uma comparação futura:

1. conta sem tarifa obrigatória e sem custo por API/integração;
2. contrato explícito para evento de Pix **enviado** ou API PF, não apenas recebimentos PJ;
3. exportação oficial em CSV/OFX com identificador estável e histórico suficiente;
4. Open Finance PF operacional e consentimento revogável;
5. comprovante compartilhável sem acesso amplo ao armazenamento;
6. autenticação forte, biometria e nenhuma entrega de credenciais a terceiros;
7. teste com pagamentos naturalmente necessários, sem mover a conta central antes da reconciliação.

**Critério de migração.** Considerar troca somente após 30 dias ou pelo menos 10 Pix reais já necessários, com ≥95% dos eventos de saída chegando em até 2 minutos, 100% reconciliáveis no extrato e zero custo recorrente. Uma notificação isolada ou demonstração de marketing não basta.

### 9. Outras alternativas legítimas e gratuitas

- **Atalho próprio “Fazer Pix”.** Abre o mesmo fluxo recomendado a partir da tela inicial; é um atalho do Finanças Conscientes, não do PicPay.
- **Tile de Configurações Rápidas “Classificar Pix”.** Após um Pix iniciado fora do app, um toque cria o rascunho e abre a classificação. O Android oferece `TileService`; continua sendo gesto explícito. Fonte: [Quick Settings tiles](https://developer.android.com/develop/ui/views/quicksettings-tiles).
- **Notificação local persistente durante uma intenção aberta.** “Voltar e classificar” reduz perda quando o usuário usa Home em vez de Voltar. É notificação do próprio app, sem observar notificações alheias.
- **Lembrete de importação do CSV.** Uma notificação local semanal ou baseada em último período importado reduz defasagem da verdade contábil. Não detecta o Pix, mas reduz a janela de incerteza.
- **OCR/parser local somente sobre comprovante escolhido pelo usuário.** Pode acelerar preenchimento sem enviar dados a servidor; a política da V2 deve continuar cifrando o resultado e descartando o bruto quando possível.
- **Meu Pluggy gratuito como experimento de atualização diária.** Apenas camada opcional e revogável, isolada da V3 principal, sem promessa de tempo real e sem substituir o CSV.

Leitura automática da área de transferência não é uma alternativa robusta: o Android restringe acesso ao clipboard em segundo plano e o conteúdo pode incluir chaves e dados sensíveis. Fonte: [mudanças de privacidade do Android 10](https://developer.android.com/about/versions/10/privacy/changes).

## Métodos explicitamente rejeitados

| Método | Avaliação | Decisão |
|---|---|---|
| Accessibility Service | Pode ler/interagir com uma interface sensível, requer concessão poderosa e teria de inferir telas mutáveis. Políticas do Google Play exigem finalidade, declaração e limites; Android também permite marcar dados financeiros como sensíveis a acessibilidade. | **Rejeitar.** Desproporcional, frágil e arrisca expor/acionar operações financeiras. |
| Captura ou leitura de tela/OCR contínuo | `MediaProjection` exige consentimento do usuário por sessão; apps podem usar `FLAG_SECURE`. Imagens conteriam saldo, chaves, nomes e comprovantes. | **Rejeitar.** Privacidade ruim, cobertura incerta e nenhuma garantia de sucesso transacional. |
| Automação da interface do PicPay | Coordenadas, textos e árvore de acessibilidade mudam; pode confirmar ação errada ou quebrar em atualização/A-B test. | **Rejeitar.** Não automatizar cliques, preenchimento, confirmação ou biometria em app bancário. |
| VPN/MITM/interceptação de tráfego | Um `VpnService` pode observar tráfego do dispositivo, mas TLS e pinning impedem leitura legítima do conteúdo; instalar CA/interceptar degrada a segurança. | **Rejeitar.** Alto risco, possível violação de termos e exposição de credenciais/dados. |
| Scraping do app ou internet banking | Sem contrato, depende de UI/HTML privado, sessão e mecanismos antifraude. | **Rejeitar.** Frágil, possivelmente contrário aos termos e com risco de bloqueio. |
| Root/desbloqueio do bootloader | Rompe premissas do sandbox/Verified Boot; desbloqueio normalmente apaga dados e amplia superfície para malware. Apps financeiros podem recusar o dispositivo. | **Rejeitar.** Incompatível com segurança da conta. |
| Coleta de senha, PIN, token ou biometria | O app não precisa desses segredos para classificação financeira. O PicPay recomenda não compartilhar senha e executar autenticação em seus canais oficiais. | **Proibir por desenho.** Nunca armazenar, transmitir ou solicitar. |
| Leitura de todas as notificações do WhatsApp | Concede acesso transversal a conversas, códigos e metadados, mas a mensagem ainda não é uma fonte contábil. | **Rejeitar.** Privacidade desproporcional e cobertura dependente de um fluxo opcional. |
| Engenharia reversa de APIs privadas | Endpoints, headers, tokens e controles antifraude não formam contrato público; podem mudar ou bloquear a conta. | **Rejeitar.** Usar apenas interfaces oficiais e documentadas. |

Fontes Android relevantes: [política de Accessibility do Google Play](https://support.google.com/googleplay/android-developer/answer/10964491), [proteção de dados sensíveis à acessibilidade](https://developer.android.com/privacy-and-security/risks/tapjacking), [MediaProjection](https://developer.android.com/media/platform/av-capture), [`FLAG_SECURE`](https://developer.android.com/reference/android/view/WindowManager.LayoutParams), [VpnService](https://developer.android.com/reference/android/net/VpnService), [sandbox de apps](https://source.android.com/docs/security/app-sandbox) e [Verified Boot](https://source.android.com/docs/security/features/verifiedboot/device-state). O PicPay também orienta a [não compartilhar senha e usar canais oficiais](https://meajuda.picpay.com/hc/pt-br/articles/360044668791-Como-eu-deixo-minha-conta-PicPay-mais-segura).

## Matriz comparativa

Escala qualitativa: **alta confiabilidade** significa que o mecanismo é bom para o papel indicado. Nenhum gatilho provisório recebe confiabilidade contábil alta; para isso existe o CSV.

| Alternativa | Latência | Cobertura | Confiabilidade como gatilho | Custo | Complexidade | Privacidade | Risco de bloqueio | Manutenção | Aderência aos termos |
|---|---:|---|---|---|---|---|---|---|---|
| **Intenção local + abrir PicPay + perguntar ao voltar** | segundos | Pix iniciados no FC | alta para lembrar; baixa como prova | zero | baixa | excelente; local | muito baixo | baixa | alta |
| **Comprovante compartilhado (`ACTION_SEND`)** | segundos/minutos, após gesto | Pix cujo comprovante é compartilhado | média/alta como evidência; não é verdade final | zero | já implementada/moderada | boa; arquivo escolhido | muito baixo | baixa/média | alta |
| **Atalho/tile próprio “Classificar Pix”** | segundos, após gesto | qualquer Pix lembrado pelo usuário | média como lembrete; baixa como prova | zero | baixa | excelente | muito baixo | baixa | alta |
| **CSV oficial importado** | horas/dias, sob demanda | transações presentes no período exportado | **alta; fonte de verdade** | zero | já implementada | excelente se local/cifrado | nenhum | baixa | alta |
| API pública PicPay para lojista | segundos para cobranças recebidas | não cobre débito da conta PF | nula para o problema | contrato comercial incerto | média/alta | fora do escopo | médio se uso indevido | média | baixa para este uso |
| Open Finance direto pelo app pessoal | potencialmente minutos | teoricamente ampla | alta semântica, mas acesso indisponível | certificações/estrutura | muito alta | dados saem para participante | não aplicável | muito alta | **não elegível** sem instituição autorizada |
| Open Finance por agregador pago | depende do plano/conector | potencialmente ampla | média/alta se contrato e conector OF | recorrente; viola requisito | alta | terceiro recebe dados | baixo se oficial | média/alta | alta se regulado/contratado |
| Meu Pluggy gratuito | até ~24 h, sem SLA | conexão PF suportada enquanto operacional | média para auditoria tardia | zero | média | dados em terceiro | baixo se somente OF oficial | média/alta | condicional |
| UsageStats do PicPay | segundos/minutos | aberturas do PicPay, não Pix | baixa; muitos falsos positivos | zero | média | ruim; histórico de uso | baixo | média | tecnicamente permitida, desproporcional |
| E-mail/extrato anexado | até 30 min após solicitação; não automático por Pix | exportações solicitadas | baixa para gatilho; boa como documento | zero | média/alta | e-mail/backend recebem finanças | baixo | alta | alta, mas inadequada |
| Assistente PicPay no WhatsApp + compartilhamento manual | segundos/minutos no fluxo específico | somente Pix feitos pelo Assistente | média como evidência manual | zero | baixa | dados também no WhatsApp | baixo | média | alta no uso oficial |
| Observar Downloads | não determinística | só arquivos baixados | baixa | zero | média | ruim se acesso amplo | baixo | alta | baixa se pedir acesso amplo |
| Notificação de saída em outra instituição | segundos, se garantida | depende da instituição | média como gatilho; baixa como verdade | conta deve ser sem tarifa | baixa/média | razoável | baixo | média | alta |
| API PF de outra instituição | segundos/minutos, se contratada | depende do contrato | potencialmente alta | desconhecido | alta | depende do fornecedor | baixo | média/alta | alta se pública e autorizada |

### Matriz dos métodos rejeitados

| Alternativa | Latência | Cobertura | Confiabilidade | Custo | Complexidade | Privacidade | Risco de bloqueio/segurança | Manutenção | Aderência aos termos |
|---|---:|---|---|---|---|---|---|---|---|
| Accessibility + automação de UI | segundos | aparente, mas dependente da tela | baixa | zero financeiro | alta | péssima | **alto** | **muito alta** | duvidosa/baixa |
| Captura/OCR de tela | segundos | telas visíveis e não protegidas | baixa/média | zero | alta | **péssima** | médio/alto | alta | duvidosa |
| VPN/MITM | incerta | tráfego, não conteúdo TLS legítimo | baixa | zero financeiro | muito alta | **péssima** | **muito alto** | muito alta | baixa |
| Scraping de app/web | minutos | fluxos que não mudarem | baixa | zero financeiro | alta | ruim | **alto** | **muito alta** | baixa/duvidosa |
| Root/bootloader | incerta | dispositivo inteiro | baixa para evento | zero financeiro | alta | **péssima** | **muito alto** | muito alta | baixa |
| Capturar credenciais/biometria | incerta | conta comprometida | inaceitável | inaceitável | alta | **inaceitável** | **crítico** | alta | **proibitiva** |
| Ler todas as notificações do WhatsApp | segundos no fluxo específico | parcial | baixa/média | zero | média | **péssima** | médio | média/alta | desproporcional |
| API privada por engenharia reversa | possivelmente segundos até quebrar | desconhecida | baixa | zero financeiro | muito alta | ruim | **alto** | **muito alta** | baixa/duvidosa |

## Arquitetura recomendada

### Opção principal: orquestração local da intenção

```text
Usuário toca “Fazer Pix”
        │
        ▼
FC grava ProvisionalPixIntent cifrada
(id, origem, createdAt, estado; valor/nota opcionais)
        │
        ▼
FC abre o launcher oficial do PicPay
        │
        ▼
Usuário opera somente no PicPay
        │
        ▼
FC volta ao primeiro plano ou é reaberto
        │
        ▼
“Você concluiu o Pix?”  Sim / Não / Depois
        │
        ├── Não ──► intenção cancelada, sem lançamento
        ├── Depois ► lembrete local, ainda sem lançamento
        └── Sim ───► classificação provisória
                            │
                            ▼
                     Importação posterior do CSV
                            │
                            ▼
                   sugestão de conciliação revisável
                            │
                  ┌─────────┴─────────┐
                  │                   │
           correspondência única   ausente/ambígua
                  │                   │
                  ▼                   ▼
          CONFIRMED_BY_CSV        NEEDS_REVIEW
```

#### Propriedades de segurança

- Dados locais cifrados conforme o modelo da V2.
- Nenhuma permissão de Internet nova.
- Nenhum Notification Listener, Accessibility, UsageStats, MediaProjection ou acesso amplo ao armazenamento.
- Nenhum valor, chave Pix, nome de destinatário ou credencial é passado ao PicPay por interface não documentada.
- O app nunca interpreta “retornou do PicPay” como “pagou”.
- Se o PicPay não estiver instalado ou não puder abrir, a intenção permanece cancelável e o app mostra instrução, sem tentar navegador ou APK alternativo.
- A declaração de visibilidade de pacote deve ser mínima. O identificador exato do pacote deve ser confirmado no aparelho/build antes da implementação e não hard-coded a partir de fonte não confiável.

#### Modelo de estado proposto

```text
CREATED
  -> EXTERNAL_APP_OPENED
  -> RETURNED_UNCONFIRMED
      -> USER_CONFIRMED_PROVISIONAL
      -> USER_CANCELLED
      -> SNOOZED

USER_CONFIRMED_PROVISIONAL
  -> CONFIRMED_BY_CSV
  -> NEEDS_REVIEW
  -> STALE_UNMATCHED
```

Campos mínimos:

- `intentId` aleatório local;
- `origin`: `IN_APP_LAUNCH`, `RECEIPT_SHARE`, `QUICK_TILE` ou `MANUAL`;
- `createdAt`, `externalOpenedAt`, `returnedAt`, `userConfirmedAt`;
- `amount` opcional e sempre editável até a conciliação;
- `counterpartyHint`/nota opcional, minimizado;
- `classificationDraft`;
- `state`, `snoozedUntil` e `matchedCsvFingerprint` opcional;
- trilha de auditoria de transições, sem conteúdo de tela do PicPay.

Uma intenção aberta não deve virar despesa automaticamente por expiração. Após sete dias ou duas importações que cubram o período sem correspondência, ela vira `STALE_UNMATCHED` e aparece para revisão/arquivamento.

### Alternativa secundária: agregador gratuito como camada diária

Se houver valor em receber movimentações antes da próxima importação manual, executar um **experimento separado** com o conector PF/Open Finance do Meu Pluggy. A atualização esperada é diária, não imediata. A arquitetura seria:

```text
PicPay --consentimento Open Finance--> participante/agregador
                                          │ atualização ~24 h
                                          ▼
                                  servidor doméstico/Free Tier
                                          │ dado provisório
                                          ▼
                                  fila local de classificação

CSV oficial ------------------------------► reconciliação final
```

Essa alternativa não deve entrar no núcleo da V3 sem avaliação de privacidade e termos. O servidor precisa validar assinatura/autenticidade do webhook, minimizar logs, cifrar dados e tolerar indisponibilidade. Mesmo assim, o CSV continua soberano. A Oracle Free Tier elimina hospedagem, não custo regulatório nem risco do fornecedor.

**Critérios para sequer iniciar:** conector explicitamente Open Finance; consentimento em canal oficial do PicPay; revogação disponível; política de retenção compreendida; nenhuma credencial PicPay entregue ao agregador fora do redirecionamento oficial; nenhuma promessa de tempo real na interface.

## Fluxo detalhado do usuário

### Pix iniciado pelo Finanças Conscientes

1. Na tela inicial, o usuário toca **Fazer Pix**.
2. O app mostra uma folha curta: “Vamos abrir o PicPay. Ao voltar, eu pergunto se o Pix foi concluído.” Valor e nota são opcionais; não pedir chave Pix na V3.
3. Ao continuar, o app grava a intenção cifrada e abre a atividade principal oficial do PicPay.
4. O usuário realiza ou cancela o Pix inteiramente dentro do PicPay, usando as proteções e biometria do próprio PicPay.
5. Ao retornar ao Finanças Conscientes, surge uma pergunta única vinculada à intenção mais recente:
   - **Sim, concluí**;
   - **Não concluí**;
   - **Perguntar depois**.
6. Em **Sim**, o app pede apenas o necessário para classificação: valor se ausente, categoria, planejado/não planejado e nota opcional. A tela deve exibir “Provisório — será confirmado pelo CSV”.
7. Em **Não**, a intenção é cancelada; nenhum lançamento é criado.
8. Em **Depois**, uma notificação do próprio app permite retomar. Ela não contém valor ou destinatário na tela bloqueada por padrão.
9. Na próxima importação do CSV, o app sugere a correspondência. O usuário resolve ambiguidades; os campos oficiais prevalecem.

Regras de UX:

- não mostrar a pergunta de retorno mais de uma vez por retomada;
- não perguntar por uma abertura comum do PicPay fora do fluxo;
- persistir o estado antes de abrir o PicPay;
- aceitar processo morto/reinício sem perder ou duplicar a intenção;
- oferecer “descartar” sempre;
- deixar visualmente distintos “provisório”, “precisa revisão” e “confirmado pelo CSV”.

### Pix iniciado diretamente no PicPay

1. Preferência: no final do Pix, usar **Compartilhar comprovante** e escolher Finanças Conscientes.
2. Alternativa: tocar no atalho/tile **Classificar Pix** imediatamente após o pagamento e preencher o mínimo.
3. Se nenhum gesto ocorrer, a transação será descoberta somente na próxima importação do CSV e entrará na caixa de classificação.

Essa limitação deve ser explícita: não há cobertura automática para o Pix iniciado fora do app.

## Experimento mínimo em aparelho Android sem fazer um Pix de teste

O experimento valida apenas a mecânica recomendada — intenção, abertura, retorno e recuperação — sem navegar até confirmação nem realizar transação.

### Preparação

Criar um spike/debug build separado ou uma tela de experimento que:

- grave apenas UUID, timestamps, estado e motivo de retorno;
- tenha o botão **Abrir PicPay (teste sem pagamento)**;
- abra a tela inicial do PicPay;
- mostre a pergunta ao voltar;
- exporte um log técnico sem dados financeiros;
- não possua Internet nem permissões especiais.

### Roteiro

Executar 20 ciclos, sempre saindo do PicPay imediatamente, sem tocar em Pix:

1. cinco vezes: abrir pelo botão e usar Voltar imediatamente;
2. três vezes: abrir, ir à tela inicial do Android e reabrir o Finanças Conscientes;
3. três vezes: abrir, bloquear/desbloquear a tela e voltar;
4. três vezes: abrir, girar a tela ou provocar recriação da Activity, se suportado;
5. três vezes: encerrar o processo do Finanças Conscientes enquanto o PicPay está aberto e reabrir o app;
6. duas vezes: toque duplo rápido no botão;
7. uma vez: simular PicPay indisponível/desabilitado, se possível sem desinstalar nem alterar proteções.

Em todos os ciclos, responder **Não concluí**. Em paralelo, fazer 20 controles abrindo/fechando o Finanças Conscientes sem tocar no botão; nenhuma pergunta deve aparecer.

### Sucesso técnico

- 20/20 intenções persistidas antes de sair do app;
- 20/20 retornos ou reaberturas mostram exatamente uma pergunta ligada à intenção;
- 0/20 perguntas nos controles sem intenção;
- zero duplicatas após toque duplo, rotação, lock/unlock ou processo morto;
- cancelamento remove a pendência sem criar transação;
- nenhuma permissão especial, dado do PicPay, screenshot ou notificação de terceiro é lida;
- abertura inicial do PicPay em tempo aceitável (meta local: p95 < 2 s, descontada a inicialização fria do próprio PicPay).

### Abandono ou ajuste do mecanismo

- **Abandonar o launcher como fluxo principal** se o PicPay não puder ser aberto por API Android pública/estável no aparelho ou se atualizações quebrarem o fluxo repetidamente.
- **Ajustar ciclo de vida/persistência** se houver qualquer duplicação ou perda de intenção; não avançar para produção até 20/20.
- **Não adicionar UsageStats/Accessibility para “consertar” retorno.** Preferir notificação local de retomada e reabertura manual.
- **Abandonar deep link específico** se ele não estiver documentado oficialmente pelo PicPay, mesmo que funcione em um build.

Depois do teste sem transação, validar a utilidade durante os primeiros 10 Pix que o usuário já faria por necessidade — nunca criar um Pix só para testar. Meta: ≥90% iniciados pelo botão ou comprovante compartilhado, 100% das intenções concluídas com pergunta, nenhuma duplicata e 100% das transações conciliáveis posteriormente.

## Plano de reconciliação com o CSV

O CSV continua sendo importado pelo mecanismo da V2. A V3 adiciona uma etapa de associação; não altera a normalização ou a identidade das linhas do arquivo.

### Dados usados para sugerir correspondência

Para cada intenção `USER_CONFIRMED_PROVISIONAL`, buscar apenas transações CSV de saída ainda não associadas e comparar:

1. **valor exato**, quando informado;
2. **janela temporal**, usando o intervalo entre `externalOpenedAt` e `userConfirmedAt`, com tolerância configurada para diferença entre hora percebida e hora do CSV;
3. **direção de saída** e tipo compatível com Pix;
4. **origem/destino normalizado**, somente se o usuário/comprovante forneceu uma pista;
5. ausência de associação anterior.

O fingerprint documentado do CSV, incluindo índice de ocorrência quando necessário, continua garantindo idempotência da importação. A intenção local tem identidade separada; nunca deve ser incorporada ao fingerprint do CSV.

### Regras de decisão

| Situação | Ação |
|---|---|
| Um candidato forte e único | Mostrar sugestão de vínculo; após confirmação, marcar `CONFIRMED_BY_CSV`. |
| Vários candidatos | `NEEDS_REVIEW`; mostrar diferenças e nunca escolher silenciosamente. |
| Nenhum candidato, mas o período ainda não foi importado | Manter provisório. |
| Nenhum candidato após período coberto | Manter separado e pedir revisão; nunca fabricar uma linha CSV. |
| Linha CSV sem intenção | Criar item na caixa de classificação normal da V2/V3. |
| Intenção cancelada e linha CSV semelhante | Tratar como transação independente; cancelamento não apaga verdade oficial. |
| Campos divergentes | CSV prevalece para data, hora, tipo, origem/destino e valor; classificação do usuário pode ser preservada. |

Como o CSV não possui identificador único documentado, mesmo uma correspondência aparentemente exata pode ser ambígua quando há dois Pix de mesmo valor no mesmo minuto. Nessas situações, revisão humana é obrigatória.

### Auditoria

Registrar localmente:

- versão do algoritmo de matching;
- IDs locais e fingerprint CSV associados;
- campos usados e score/motivo da sugestão;
- confirmação ou rejeição do usuário;
- eventual desfazer do vínculo.

Não registrar conteúdo bruto do comprovante, senha, chave Pix completa ou tela do PicPay. A importação repetida do mesmo CSV deve ser idempotente e não reabrir classificação já resolvida.

## Impacto na V2 e escopo proposto para a V3

### O que permanece inalterado

- CSV como fonte de verdade;
- importação explícita pelo usuário;
- banco local cifrado e Android Keystore;
- compartilhamento de comprovante como entrada provisória;
- ausência de servidor e de permissão de Internet no produto principal;
- tratamento manual de ambiguidades e deduplicação conforme esquema documentado.

### Escopo da V3

1. entidade `ProvisionalPixIntent` e máquina de estados;
2. botão e atalho próprio **Fazer Pix**;
3. abertura segura da atividade principal do PicPay;
4. pergunta de retorno com **Sim / Não / Depois**;
5. persistência idempotente diante de rotação, lock e processo morto;
6. notificação local discreta para intenção adiada;
7. estados visuais “provisório”, “revisão” e “confirmado pelo CSV”;
8. matching sugerido entre intenção/comprovante e CSV, com auditoria e desfazer;
9. atalho ou tile opcional **Classificar Pix** para fluxo externo;
10. telemetria apenas local e agregada para medir cobertura, salvo exportação voluntária.

### Fora do escopo da V3

- Open Finance/agregador em produção;
- backend doméstico ou Oracle Cloud;
- iniciação regulada de pagamento;
- monitoramento de e-mail, WhatsApp, arquivos, clipboard ou uso de outros apps;
- Notification Listener, UsageStats, Accessibility ou captura de tela;
- automação de interface, root, MITM, scraping ou API privada;
- escolha/migração de instituição financeira.

### Métricas de sucesso após uso natural

Avaliar após 30 dias e pelo menos 10 Pix reais já necessários:

- ≥90% dos Pix iniciados pelo botão ou seguidos de compartilhamento/tile;
- pergunta exibida em 100% das intenções retornadas/reabertas;
- mediana entre retorno e classificação < 30 s;
- <5% de falsos registros provisórios após a resposta do usuário;
- 100% das linhas do CSV importado preservadas/idempotentes;
- ≥90% das intenções conciliadas com candidato único ou revisão simples;
- 0 vínculos silenciosos ambíguos;
- 0 permissões sensíveis novas, 0 credenciais financeiras coletadas e R$ 0 de custo recorrente.

### Critérios de abandono da V3 proposta

Abandonar ou redesenhar se qualquer um ocorrer de modo persistente:

- adoção do botão/compartilhamento <70% após quatro semanas;
- >10% de prompts duplicados, perdidos ou indevidos;
- matching ambíguo em >20% das intenções apesar de valor e janela temporal;
- necessidade de permissão sensível para funcionar;
- atualização do PicPay impede o launcher público de modo recorrente;
- o fluxo acrescenta mais esforço do que classificar a caixa do CSV;
- qualquer incidente de exposição de comprovante/dados na tela bloqueada.

Se a adoção ficar entre 70% e 90%, testar primeiro melhorias de UX — atalho na tela inicial, tile, notificação de retomada e menos campos — antes de considerar Open Finance ou troca da conta.

## Decisão recomendada

Construir a V3 como uma **orquestração local de intenção**, não como observador do PicPay. O produto deve assumir explicitamente:

> “Se você começar o Pix por aqui, eu lembro de perguntar assim que você voltar. Se começar fora, compartilhe o comprovante ou use o atalho. O CSV confirma tudo depois.”

Essa solução não alcança automação total, mas é a melhor combinação encontrada de latência, simplicidade, segurança, auditabilidade, custo zero e aderência às interfaces públicas. Ela melhora o comportamento humano sem fingir acesso bancário que o sistema não possui.

Open Finance via agregador gratuito fica como pesquisa secundária de detecção diária, não como dependência. Troca de conta só deve ser reavaliada com evidência contratual e um piloto natural. Todos os caminhos que inspecionam ou automatizam secretamente o app bancário devem permanecer fora do projeto.

## Referências oficiais principais

### PicPay

- [Portal PicPay Developers](https://developers-business.picpay.com/)
- [API Pix — autenticação](https://developers-business.picpay.com/pix/en/docs/authentication)
- [API Pix — webhook de cobrança](https://developers-business.picpay.com/pix/docs/webhook)
- [Central de Ajuda — Open Finance](https://meajuda.picpay.com/hc/pt-br/categories/4407869759635-Open-Finance)
- [Central de Ajuda — extrato de conta](https://meajuda.picpay.com/hc/pt-br/sections/360011869332-Extrato-de-conta)
- [Como compartilhar comprovante](https://meajuda.picpay.com/hc/pt-br/articles/48204456769939-Como-acessar-um-comprovante-de-pagamento-no-Assistente-de-Pagamentos)
- [Pix pelo Assistente no WhatsApp](https://meajuda.picpay.com/hc/pt-br/articles/33132134741395-Como-eu-fa%C3%A7o-uma-transfer%C3%AAncia-Pix-pelo-WhatsApp)
- [Segurança da conta PicPay](https://meajuda.picpay.com/hc/pt-br/articles/360044668791-Como-eu-deixo-minha-conta-PicPay-mais-segura)

### Banco Central e Open Finance Brasil

- [FAQ Open Finance do Banco Central](https://www.bcb.gov.br/meubc/faqs/s/open-finance)
- [Resolução Conjunta nº 1](https://normativos.bcb.gov.br/Lists/Normativos/Attachments/51028/Res_Conj_0001_v8_P.pdf)
- [API de Contas](https://openfinancebrasil.atlassian.net/wiki/spaces/OF/pages/207716736)
- [Orientações da API de Contas](https://openfinancebrasil.atlassian.net/wiki/spaces/OF/pages/1739096252/Orienta%2Bes%2B-%2BDC%2BContas)
- [Referência de limites e desempenho](https://openfinancebrasil.atlassian.net/wiki/spaces/OF/pages/17957025)
- [Webhook de Pagamentos](https://openfinancebrasil.atlassian.net/wiki/spaces/OF/pages/1436549347/Pagamentos%2B-%2BWebhook%2B-%2Bv1.3.0)
- [Padrão de certificados](https://openfinancebrasil.atlassian.net/wiki/spaces/OF/pages/245694518)
- [Diretrizes de certificação](https://openfinancebrasil.atlassian.net/wiki/spaces/OF/pages/280297514/Diretrizes%2BGerais%2Bde%2BCertifica%2Bo%2Bde%2BConformidade)

### Android

- [Intents e interação entre apps](https://developer.android.com/training/basics/intents/)
- [Visibilidade de pacotes](https://developer.android.com/training/package-visibility/declaring)
- [App Links e deep links](https://developer.android.com/training/app-links/create-deeplinks)
- [UsageStatsManager](https://developer.android.com/reference/android/app/usage/UsageStatsManager)
- [Eventos de uso](https://developer.android.com/reference/android/app/usage/UsageEvents.Event)
- [Compartilhamento com `ACTION_SEND`](https://developer.android.com/develop/ui/compose/sharing/receive)
- [Armazenamento compartilhado e SAF](https://developer.android.com/training/data-storage/shared/media)
- [Acesso amplo a arquivos](https://developer.android.com/training/data-storage/manage-all-files)
- [Quick Settings tiles](https://developer.android.com/develop/ui/views/quicksettings-tiles)

### Fornecedor citado na alternativa secundária

- [Pluggy — preços e plano de uso pessoal](https://www.pluggy.ai/precos)
- [Pluggy — atualização de conexões do Meu Pluggy](https://v2.docs.pluggy.ai/pt/docs/connections/item)
- [Pluggy — status dos conectores](https://status.pluggy.ai/)
