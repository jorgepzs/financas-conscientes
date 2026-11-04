# Observações em aparelho Android de teste

Use esta página como caderno de validação. Não cole valores reais, nomes de pessoas, identificadores, capturas de tela ou texto integral de alertas. Registre somente comportamento e disponibilidade de campos.

## Ficha do aparelho

- Modelo: não registrado no repositório
- Ambiente observado: Android 16 (API 36)
- Versão instalada do PicPay: preencher no dispositivo se necessário
- Data da validação: 2026-10-03
- Versão do APK: 0.1.0 debug

## Resultados

| Caso | O serviço recebeu evento? | Tipo | Valor | Contraparte | Observações sem dados pessoais |
|---|---|---|---|---|---|
| Pix enviado | não | ausente | ausente | ausente | O PicPay não publicou notificação Android para a operação observada. |
| Pix recebido | sim | a observar | a observar | a observar | O PicPay publicou notificação Android. Não registrar conteúdo financeiro neste documento. |
| Pagamento no débito/cartão | | | | | |
| Notificação agrupada | | | | | |
| App forçado a parar/reiniciar | | | | | |

## Confiabilidade percebida

- O app permaneceu ativo após reinício? (não avaliado)
- Alguma configuração de bateria precisou mudar? (não avaliado)
- Notificações duplicadas ou atrasadas? (não avaliado)
- A tela do sistema mostra o listener habilitado? Sim; também confirmado por ADB.

Os canais registrados pelo pacote `com.picpay` estavam habilitados, com importância normal ou alta. Não foi encontrado canal específico de “Pix enviado” desativado. A ausência observada é, portanto, limitação do evento publicado pelo PicPay, não falha do listener.

## Encerramento

Limpe o histórico do app depois de concluir o diagnóstico, caso não precise mais dos exemplos locais.
