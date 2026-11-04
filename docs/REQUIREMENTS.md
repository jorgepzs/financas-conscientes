# Requisitos e critérios de qualidade

## Requisitos funcionais do diagnóstico

- RF-01: mostrar claramente se o acesso a notificações foi concedido.
- RF-02: abrir a tela de configurações apropriada para habilitar o listener.
- RF-03: persistir somente notificações do pacote exato `com.picpay`.
- RF-04: mostrar título, texto e horário disponíveis para os últimos eventos.
- RF-05: limitar armazenamento a 100 eventos e retenção máxima de 30 dias.
- RF-06: apagar os dados locais sob ação explícita do usuário.
- RF-07: funcionar sem conexão de rede.

## Requisitos da aplicação principal

- RF-08: aceitar compartilhamento explícito de comprovante em formatos oferecidos pelo PicPay.
- RF-09: perguntar categoria, finalidade e planejado/impulso sem exigir conservar o comprovante bruto.
- RF-10: importar localmente o CSV oficial do PicPay após validar seu layout real.
- RF-11: tratar cada saída do CSV sem classificação como pendência revisável.
- RF-12: usar valor, data, tipo e contraparte do CSV para dados confirmados.
- RF-13: sinalizar reconciliações ambíguas para decisão humana.
- RF-14: impedir duplicação quando o mesmo CSV for importado novamente.
- RF-15: calcular totais mensais somente a partir de saídas confirmadas pelo CSV.
- RF-16: manter registros manuais ou originados de comprovantes fora dos totais enquanto forem provisórios.
- RF-17: permitir orçamento mensal recorrente e mostrar valor disponível ou excedido.
- RF-18: separar valores classificados como planejado, impulso e não informado.
- RF-19: permitir editar e excluir classificações; a exclusão não deve apagar a transação importada.
- RF-20: apresentar o compartilhamento de comprovante como fluxo principal de captura imediata.
- RF-21: permitir excluir uma ou todas as movimentações importadas sem destruir classificações e orçamento.
- RF-22: ao excluir uma movimentação vinculada, transformar sua classificação em provisória.
- RF-23: permitir restaurar uma movimentação excluída pela reimportação do CSV correspondente.

## Requisitos de segurança e privacidade

- RS-01: não pedir senha, PIN, token, número de cartão ou acesso ao internet banking.
- RS-02: não capturar tela, acessibilidade, teclado, clipboard ou notificações de outros apps.
- RS-03: criptografar o registro persistido usando chave mantida pelo Android Keystore.
- RS-04: desabilitar backup do app.
- RS-05: não declarar permissão `INTERNET` enquanto não houver um recurso remoto justificado.
- RS-06: truncar textos e não salvar extras Android completos.
- RS-07: deixar explícito que o histórico é diagnóstico e não extrato financeiro.
- RS-08: não usar Accessibility Service, captura de tela ou automação da interface do PicPay.
- RS-09: processar comprovantes e CSV localmente, sem transmissão para servidor.
- RS-10: não persistir o arquivo bruto do comprovante quando os dados mínimos já tiverem sido capturados.
- RS-11: a aplicação principal não deve declarar `NotificationListenerService`; o listener fica isolado no artefato diagnóstico.

## Limites

O sistema operacional e o PicPay controlam quando uma notificação é publicada e que conteúdo ela contém. O app não enxerga operações que não geram notificação; isso foi confirmado para Pix enviado. `NotificationListenerService` exige autorização especial do usuário e pode sofrer restrições de segundo plano específicas do fabricante. O compartilhamento de comprovante depende de ação do usuário, e a completude final depende de importação periódica do CSV.

## Adendo V5.1 / 0.5.2

Este adendo atualiza RF-08/09/13/15/16 e RS-10 para a revisão V5.1, sem alterar os requisitos históricos do diagnóstico:

- PDF, PNG, JPEG e WebP escolhidos ou compartilhados pelo usuário são limitados a 10 MB e persistidos cifrados em armazenamento privado sem backup. Texto, nome e origem do arquivo não bastam.
- A assinatura reconhecida e a tag criptográfica autenticam formato/integridade do arquivo, não sua origem, semântica, valor ou relação com PicPay/Pix.
- O painel soma saídas CSV e anexos íntegros fornecidos pelo usuário; manual sem anexo íntegro não soma. Não existe interpretação automática de Pix.
- Supressão temporária de dupla contagem é restrita a saída CSV ainda sem vínculo, mesmo valor e data reconhecida em até 48 horas. Candidatos históricos de mesmo valor são apenas para revisão manual.
- Vínculo ao CSV exige revisão e salvamento explícitos. Ao classificar uma saída, sempre há uma opção visível para criar novo gasto, mesmo se houver um candidato único.
- A 0.5.1 migra sem prova por anexo registros antigos que não tinham arquivo persistido. Comprovante de prova vem de blob íntegro; prova por extrato deriva de fingerprint ainda importado.
- Visualização PDF pode criar cópia plaintext temporária no cache privado, removida ao fechar/erro ou na próxima abertura do visualizador após término abrupto.
