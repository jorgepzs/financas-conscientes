# Estrutura e manutenção

## Diretórios

- `repositories/`: código e configurações versionáveis, um diretório por repositório.
- `docs/`: documentação global, decisões e orientações inter-repositório.
- `work/`: rascunhos temporários sem valor de fonte de verdade.
- `outputs/`: reservado para entregáveis exportados, caso necessário.

Os repositórios de código mantêm documentação técnica própria e apontam para esta pasta-mãe quando suas decisões tiverem alcance maior que o app.

Dentro de `repositories/app-android/`, o módulo `app/` é o diagnóstico 0.1 e `app-v2/` contém a aplicação principal V5.1 / Android 0.5.2. Os APKs finais para uso ficam em `artifacts/`; diretórios `build/` são saídas reproduzíveis do Gradle.

## Convenções

- Documentação em português; nomes de código em inglês quando forem nomes convencionais de API.
- Não armazenar dados reais de banco no Git.
- Pequenas mudanças funcionais acompanhadas de critério de aceitação na documentação.
- Criar ADR para decisões que afetam privacidade, fonte de verdade, infraestrutura ou custos.
- Registrar versão do Android, PicPay e app nas observações de dispositivo, sem capturar conteúdo financeiro.

## Revisão de custos

O MVP atual não usa serviços pagos nem rede. Antes de acrescentar backend, Open Finance ou mensageria, documentar custo recorrente esperado, alternativa gratuita, limites de uso e plano para desligar o serviço.
