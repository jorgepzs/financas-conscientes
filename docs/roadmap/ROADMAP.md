# Roadmap

## Fase 0 — Estrutura e documentação (em andamento)

- [x] Criar pasta-mãe com documentação fora do repositório de código.
- [x] Colocar repositório Android sob `repositories/`.
- [x] Definir princípios iniciais de privacidade e fonte de verdade.

## Fase 1 — App de diagnóstico

- [x] Interface mínima para o estado da permissão e lista de eventos.
- [x] Listener filtra estritamente `com.picpay`.
- [x] Persistência local cifrada e limitada.
- [x] Compilar em ambiente com Android SDK.
- [x] Instalar e validar em aparelho Android de teste.
- [x] Confirmar que Pix enviado não gera notificação e Pix recebido gera evento.
- [ ] Documentar campos disponíveis no evento de Pix recebido sem registrar dados pessoais.
- [ ] Verificar comportamento após reinício e políticas de bateria.

## Fase 2 — Captura imediata por comprovante

- [x] Disponibilizar categorias e decisões configuráveis, com cores por categoria e preservação de registros ao renomear/remover.
- [x] Fazer o app aparecer no menu Compartilhar para texto, imagem ou PDF de comprovante.
- [x] Ao receber um comprovante, perguntar categoria, finalidade e planejado/impulso sem conservar o documento bruto.
- [x] Tornar o fluxo “comprovante agora, CSV depois” explícito na tela inicial e na confirmação de salvamento.
- [x] Criar a aplicação principal sem acesso global às notificações; manter o APK atual apenas como diagnóstico.
- [x] Registro de planejado/impulso com cancelamento sem salvar.
- [ ] Não contar notificações como gasto definitivo.

## Fase 3 — Fonte oficial e conciliação

- [x] Confirmar que o PicPay oferece extrato em CSV e PDF com saídas e entradas.
- [x] Processar amostra privada e documentar somente cabeçalhos/codificação do CSV.
- [x] Implementar importação local do CSV confirmado.
- [x] Sugerir associação única por valor e janela temporal, sempre com revisão humana.
- [x] Deixar saídas não reconhecidas como pendência revisável.
- [x] Permitir apagar uma ou todas as movimentações importadas, preservando classificações como provisórias.
- [ ] Verificar no aparelho a contagem importada contra o extrato exibido pelo PicPay.

## Fase 4 — Painel local

- [x] Resumo mensal baseado somente nas saídas confirmadas pelo CSV.
- [x] Totais por categoria e por decisão planejada/impulso.
- [x] Orçamento mensal recorrente com valor disponível ou excedido.
- [x] Edição e exclusão individual de classificações.
- [x] Navegação inferior entre Início, Resultados e Registros.
- [x] Menu de utilidades no lado esquerdo do cabeçalho.
- [x] Maiores gastos e indicadores de qualidade/atualização do extrato.
- [x] Categorias personalizadas disponíveis na classificação.
- [x] Aparência selecionável (claro/escuro/seguir o sistema), sem mexer nos dados financeiros.
- [ ] Tendência de gastos classificados como impulso.
- [ ] Exportação dos dados classificados.
- [x] Exclusão total dos dados e exclusão individual de classificações.
- [ ] Avaliar interface web apenas depois de a captura e conciliação funcionarem.

## Fase 5 — Sincronização opcional

- [ ] Comparar operação apenas local versus VPS pessoal.
- [ ] Documentar modelo de ameaça, backup, autenticação e exclusão.
- [ ] Estimar custos diretos e de manutenção, mesmo para free tier.
- [ ] Implementar somente com necessidade clara e aprovação do usuário.

## Fase 6 — Lembretes de comprovante (experimental)

- [x] Opt-in local de uso do app exato `com.picpay`.
- [x] Serviço visível em primeiro plano e notificação inicial/final com debounce.
- [x] Suprimir lembrete depois de PDF/imagem recebidos e cifrados via `ACTION_SEND`.
- [x] Não converter lembretes em registros ou valores financeiros.
- [x] Alinhar formulário nativo de 0.6 ao preview V5.1 sem alterar o artefato estável 0.5.2.
- [ ] Testar em aparelho Android sem Pix real: abrir/alternar PicPay, compartilhar comprovante antigo, CSV/texto inválidos, lock screen e demais apps.
- [ ] Avaliar continuidade do serviço sob gerenciamento de bateria do fabricante.

## Critério de avanço

Cada fase só avança depois de seu critério observável estar satisfeito. Não conectar APIs pagas, mensageria ou contas bancárias por suposição de custo zero.
