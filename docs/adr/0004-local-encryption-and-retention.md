# ADR 0004: Cifragem local e retenção curta

- Estado: aceito para o MVP
- Data: 2026-10-03

## Contexto

Notificações podem incluir nomes e valores privados. Para diagnosticar campos no aparelho, é útil sobreviver ao encerramento do app, mas guardar texto claro indefinidamente não é necessário.

## Decisão

Guardar apenas identificador aleatório, horário, pacote verificado, título e um campo de texto truncado. Cifrar o JSON com AES-GCM; guardar a chave no Android Keystore; persistir o arquivo no diretório interno excluído de backup. Limitar a 100 eventos e 30 dias. Expor uma ação para apagar arquivo e chave.

## Consequências

- O histórico não é portável nem recuperável se a chave do Keystore for perdida.
- O arquivo fica cifrado em repouso, mas o texto aparece na interface depois que o aparelho é desbloqueado.
- Não usar screenshot facilita privacidade, mas também bloqueia capturas legítimas para suporte.
- Uma futura sincronização exigiria nova decisão, pois a chave local não é chave de servidor.
