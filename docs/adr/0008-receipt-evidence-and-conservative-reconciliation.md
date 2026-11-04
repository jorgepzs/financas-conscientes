# ADR 0008 — Evidência por comprovante e conciliação conservadora

## Status

Aceita para V5.1 / Android 0.5.2 em 3 de outubro de 2026; substitui parcialmente a ADR 0006. O CSV continua sendo a referência de movimentações importadas.

## Contexto

O usuário pode registrar um gasto no momento em que ocorre, antes de importar o extrato. Um arquivo recebido como texto ou apenas seu nome não é evidência suficiente. Um documento anexado pelo usuário pode contribuir para os resultados, mas assinatura de arquivo e integridade criptográfica não verificam origem PicPay, conteúdo bancário ou que represente um Pix. O sistema não cria vínculo automático com uma linha do CSV.

## Decisão

- Um registro pode ter evidência `NONE`, `RECEIPT`, `STATEMENT` ou `BOTH`. A evidência de extrato deriva de um fingerprint que ainda exista entre as transações importadas; a evidência de comprovante exige um blob cifrado íntegro e reconhecido.
- PDF, PNG, JPEG e WebP escolhidos/compartilhados são copiados em fluxo limitado a 10 MB, validados por assinatura de formato e cifrados com AES-GCM no armazenamento privado excluído de backup. A autenticação GCM protege integridade da cópia, não sua semântica. Não se guardam URI de origem, nome original nem conteúdo compartilhado em texto.
- As saídas importadas continuam sendo somadas pelo valor do CSV. Registro manual sem comprovante válido não entra nos totais. Registro com comprovante válido pode entrar antes da importação do CSV.
- Para evitar uma duplicação óbvia, um registro comprovado só fica temporariamente fora do total quando há uma saída CSV ainda sem classificação, com valor exato igual e data reconhecida em até 48 horas do registro. O CSV conta uma vez; a UI exibe “Possível conciliação”. Se data/valor não se encaixarem, o registro comprovado continua nos totais.
- Ao classificar uma saída, o app pode sugerir registros próximos e comprovantes antigos com valor igual. Sugestões históricas são somente para revisão manual; nunca suprimem totais por si só. Nenhum fingerprint é escrito até a pessoa revisar e salvar.
- CSV importado permanece pendente como antes. Importar arquivo, compartilhar texto ou compartilhar apenas nome de arquivo não cria prova por comprovante.
- Anexos permanecem ligados a um registro editável; substituir, remover ou excluir o registro apaga o blob cifrado correspondente.

## Migração

Campos novos são opcionais. Registros de 0.5.1 sem blob persistido mantêm ausência de evidência por comprovante, mesmo que tenham sido originalmente criados por `ACTION_SEND`. Um fingerprint que ainda corresponda a uma linha importada continua sendo prova por extrato; CSV já classificado não recebe novo vínculo.

## Consequências e limites

- O valor é informado/confirmado pela pessoa e não é extraído automaticamente; um anexo pode não ser comprovante, pode estar inválido/estornado ou representar outro tipo de compra.
- A janela de 48 horas reduz falsas duplicações históricas, mas não prova identidade; transações repetidas com mesmo valor e data podem pedir revisão, e registros fora da janela podem ser contados ao lado da transação CSV.
- PDF é renderizado dentro do app. Para `PdfRenderer`, uma cópia plaintext temporária fica no cache privado enquanto o visualizador está aberto; erro e fechamento tentam removê-la, e a próxima abertura varre somente arquivos `receipt-preview-*.pdf` próprios. Se o processo for morto, a cópia pode persistir até essa próxima abertura.
- Sem Internet, sincronização, leitura de notificações, automação, OCR ou inferência de Pix.
