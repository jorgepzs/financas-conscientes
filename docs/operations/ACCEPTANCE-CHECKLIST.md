# Checklist de aceite manual — MVP Android

Marque a data, versões do aparelho e resultado geral em `../research/DEVICE-OBSERVATIONS.md`. Não anexe conteúdo financeiro real ao projeto.

## Build e instalação

- [x] Sincronização Gradle termina sem erro com AGP 9.4.1 / Gradle 9.6.0 / JDK 17.
- [x] APK `debug` é gerado.
- [x] APK instala e abre em aparelho Android de teste.
- [x] Nenhuma tela solicita credenciais, PIN ou permissão de Internet.

## Permissão

- [ ] Estado começa como desativado antes da concessão.
- [ ] Botão abre a tela de acesso especial do Android.
- [ ] Após habilitar e voltar, o estado aparece como ativo.
- [ ] Após revogar e voltar, estado aparece como desativado.

## Filtro e conteúdo

- [ ] Uma notificação PicPay aparece na lista.
- [x] Confirmado que Pix enviado pode não gerar notificação do PicPay, mesmo com os canais habilitados.
- [ ] A lista exibe hora, título e texto fornecidos ao app.
- [ ] Texto longo não excede o limite esperado.
- [ ] Uma notificação de outro aplicativo não aparece nem altera o contador.
- [ ] Quando não há título ou texto, a lista informa a ausência sem inventar dados.

## Persistência e exclusão

- [ ] Um evento continua disponível após fechar e reabrir o app.
- [ ] Arquivo do histórico fica em `noBackupFilesDir` e seu conteúdo não é texto claro.
- [ ] O app não inclui histórico no backup Android.
- [x] A tela permite screenshots e preview, conforme a decisão atual do produto; o APK não usa `FLAG_SECURE`.
- [ ] “Apagar histórico” pede confirmação, remove eventos e invalida a chave local.
- [ ] O estado sem eventos é mostrado corretamente depois da exclusão.
- [ ] Exceder 100 eventos mantém somente os 100 mais recentes.
- [ ] Evento com mais de 30 dias é descartado na leitura/retention seguinte.

## Estabilidade

- [ ] Abrir e fechar várias vezes não registra duplicatas por causa apenas da tela.
- [ ] Testar depois de reiniciar o aparelho.
- [ ] Testar após período de tela apagada sem alterar bateria, anotar se há atraso/ausência.
- [ ] Se necessário, testar uma configuração de bateria por vez e registrar o resultado.

## Limite de aceite

Este checklist comprova comportamento do app no aparelho, não integridade do extrato ou total de gastos. A ausência de notificação não prova ausência de movimentação.

## V2 — registro e CSV

- [x] APK 0.2.0 instala e abre em aparelho Android de teste.
- [x] APK não declara permissões Android.
- [x] Parser reconhece a amostra real sem registrar seu conteúdo nos testes.
- [x] CSV é importado e uma classificação pode ser salva.
- [x] Estado cifrado permanece idêntico após `force-stop` e abertura fria.
- [x] Arquivo fica em `no_backup` com acesso restrito ao UID do aplicativo.
- [ ] Comparar a quantidade de movimentações e saídas com o extrato do PicPay.
- [ ] Compartilhar um comprovante existente e confirmar que o formulário abre.
- [ ] Confirmar que uma segunda importação do mesmo CSV adiciona zero movimentações.

## V3 — painel e revisão

- [x] APK 0.3.0 atualiza a instalação existente e abre em aparelho Android de teste.
- [x] O arquivo cifrado existente permanece byte a byte idêntico após a atualização.
- [x] Testes garantem que o painel usa o valor do CSV, não o valor provisório digitado.
- [x] Registros provisórios são excluídos dos totais mensais.
- [ ] Comparar o total do mês exibido com o total de saídas do CSV/PicPay.
- [ ] Definir, alterar e remover o orçamento mensal no aparelho.
- [ ] Editar uma classificação e confirmar a atualização no painel.
- [ ] Excluir uma classificação confirmada e confirmar que a saída volta às pendências.

## V4 — comprovantes e gerenciamento do extrato

- [x] Uma revisão funcional do APK 0.4.0 atualiza a instalação existente e abre em aparelho Android de teste.
- [x] Instalar o artefato final 0.4.0 com a nova interface profissional sem abrir o aplicativo.
- [ ] Revisar visualmente a nova interface em aparelho Android de teste.
- [x] O arquivo cifrado existente permanece byte a byte idêntico após a atualização.
- [x] Testes confirmam que apagar uma transação preserva a classificação como provisória.
- [x] Testes confirmam que apagar todo o extrato preserva classificações e orçamento.
- [ ] Compartilhar um PDF ou imagem do PicPay e concluir o novo formulário.
- [ ] Apagar uma movimentação sem classificação e confirmar sua remoção do painel.
- [ ] Apagar uma movimentação classificada e confirmar que o registro permanece provisório.
- [ ] Reimportar o CSV e confirmar que uma movimentação apagada retorna.
- [ ] Testar **Apagar extrato** somente depois de conservar uma cópia do CSV.

## V4.1 — menu superior

- [x] APK 0.4.1 compila, passa nos dez testes e não apresenta problemas no lint.
- [x] APK 0.4.1 instala sobre a 0.4.0 preservando o arquivo cifrado.
- [ ] Abrir o menu de três pontos e conferir os sete atalhos.
- [ ] Confirmar que o menu cabe na tela e é confortável de usar com uma mão.

## V5 — navegação e resultados

- [x] APK 0.5.0 compila, passa nos quinze testes e não apresenta problemas no lint.
- [x] O APK 0.5.0 não declara permissões Android e não contém `FLAG_SECURE`.
- [x] Categorias personalizadas estão centralizadas e disponíveis no seletor de classificação.
- [ ] Instalar a 0.5.0 sobre a 0.4.1 e confirmar que os dados cifrados existentes foram preservados.
- [ ] Conferir as abas inferiores **Início**, **Resultados** e **Registros** em aparelho Android de teste.
- [ ] Confirmar que o botão à esquerda abre as sete utilidades e que ações destrutivas continuam identificadas.
- [ ] Comparar total, orçamento, categorias, planejado/impulso e maiores gastos com o CSV importado.
- [ ] Navegar entre meses e confirmar que não é possível avançar além do mês atual.
- [ ] Tirar uma captura de tela da interface e confirmar que o Android não a bloqueia.
