# Arquitetura inicial

## Contexto

A notificação do banco é útil para perguntar logo, mas não é fonte contábil confiável: pode não chegar, ser resumida, ocultar conteúdo ou não representar o estado final da transação. A arquitetura futura deve distinguir observações provisórias de transações confirmadas por extrato.

## Fase de diagnóstico atual

```text
PicPay (com.picpay)
       │ NotificationListenerService do Android
       ▼
Filtro exato do packageName
       │ título/texto, tamanho limitado
       ▼
Armazenamento criptografado local (Keystore + AES-GCM)
       │
       ├── Tela de permissão
       ├── Histórico local de eventos
       └── Ação para limpar
```

O filtro ocorre antes da leitura dos campos da notificação. O serviço não persiste nem encaminha notificações de outros pacotes. A aplicação não declara permissão de internet.

## Dados do evento

- Identificador aleatório local.
- Horário de publicação informado pelo Android.
- Nome do pacote fixo após filtro (`com.picpay`).
- Título e texto visíveis, truncados a 600 caracteres cada.
- Sem extras brutos, `PendingIntent`, ícones, imagens, histórico de notificações ou conteúdo da tela do banco.

O conteúdo é sensível. O arquivo é privado ao app, autenticado e criptografado com AES-GCM usando uma chave não exportável do Android Keystore. Limite: 100 eventos e 30 dias. O backup Android é desativado. O usuário pode apagar o arquivo pela interface.

## Captura e reconciliação após a validação no aparelho

Somente após validar o diagnóstico, avaliar um fluxo de três estados:

```text
Comprovante compartilhado (provisório)
       │ pergunta de categoria / intenção
       ▼
Registro local aguardando conciliação
       │ importação do CSV oficial do PicPay
       ▼
Transação confirmada (valor do extrato prevalece)
```

O listener de notificações fica restrito ao protótipo diagnóstico: a observação real mostrou notificação de Pix recebido e ausência de notificação de Pix enviado. A aplicação principal não pedirá esse acesso sensível. Saídas do CSV sem registro provisório entram numa fila de classificação.

Uma futura integração só deve avançar com fonte legítima de dados, custos conhecidos, consentimento claro, gestão de segredos, cópias de segurança e uma política de exclusão. O saldo exibido como definitivo deve vir de fonte bancária, não de contar notificações.

## Componentes Android

- `MainActivity`: estado do acesso e histórico.
- `PicPayNotificationListener`: serviço vinculado ao Android; ignora todos os pacotes exceto `com.picpay`.
- `EncryptedEventStore`: persistência interna cifrada, limites de retenção e limpeza.
- `DiagnosticEvent`: modelo explícito, sem extras genéricos do Android.

### Aplicação principal V5.1 / 0.5.2 e experimento 0.6.5

- `MainActivity`: experiência guiada “anexo escolhido pelo usuário agora, CSV depois”, menu lateral, seletor CSV, painel mensal, orçamento, pendências e revisão de conciliações.
- `PicPayCsvParser`: reconhecimento conservador do esquema observado, valores brasileiros, sinal Unicode, data/hora e fingerprints estáveis.
- `EncryptedFinanceStore`: estado normalizado cifrado com AES-GCM em `noBackupFilesDir`; campos de anexos são opcionais para migração retrocompatível.
- `EncryptedReceiptAttachmentStore`: anexo escolhido pelo usuário, assinatura de formato, limite de 10 MB, GCM e retenção privada sem backup.
- `ReceiptViewerActivity`: visualização interna; PDF tem cópia plaintext transitória no cache privado, removida ao fechar ou varrida na próxima abertura.
- `FinanceModels`: separação entre transação importada, classificação e evidência `NONE`/`RECEIPT`/`STATEMENT`/`BOTH`.
- `MonthlyDashboardCalculator`: cálculo puro por mês; soma CSV e anexos íntegros, com supressão limitada a saída não classificada de mesmo valor/data até 48 horas.
- `ReceiptReconciliation`: candidatos de supressão próximos são distintos das sugestões históricas de revisão; nenhum vínculo é salvo automaticamente.
- `FinanceStateMutations`: remoções testáveis de uma ou todas as transações, desvinculando classificações em vez de destruí-las.
- `ExpenseSettingsMutations`: cadastro, renomeação e remoção testáveis de categorias/decisões; renomear acompanha classificações existentes e remover reclassifica para os fallbacks sem tocar valores/anexos.
- `ExpenseCategoryPalette`: paleta local de cores por categoria, persistida dentro do mesmo estado AES-GCM e consumida pela distribuição mensal.
- `AppAppearance`: preferência local de tema claro/escuro/automático, aplicada antes da criação das telas e também ao visualizador de comprovantes; não altera o estado financeiro cifrado.
- `EncryptedFinanceStore`: schema 4 adiciona listas de categoria/decisão e mapa categoria-cor; campos ausentes de estados antigos migram para as listas padrão.

A aplicação principal usa outro pacote (`br.com.financasconscientes`) e pode coexistir com o diagnóstico (`br.com.financasconscientes.diagnostic`). As atualizações mantêm o pacote e o arquivo cifrado. Não declara permissões nem componente de listener.

## Decisões técnicas

- Kotlin e APIs nativas Android, sem biblioteca externa no MVP.
- `minSdk 26`; Java 17; `compileSdk 37`; `targetSdk 37`.
- Nenhuma dependência de rede no app.
- Interface XML/Views simples para facilitar depuração e reduzir o conjunto de dependências.

Consulte os ADRs em `docs/adr/`.
