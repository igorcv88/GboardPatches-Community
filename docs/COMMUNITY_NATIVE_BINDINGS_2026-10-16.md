# Integrações nativas 18.0.3 — progresso de 16/10/2026

Este trabalho parte de `ecaf8778dd900d040ce0d2a05d6d4c1092625982`: os PRs #1, #2 e #3 já estavam mesclados quando esta sessão começou. Não foram reabertos nem alterados. Este complemento ainda não está pronto para merge/release.

## Binário conferido

APK original: `com.google.android.inputmethod.latin`, versão `18.0.3.954559732-release-arm64-v8a`, versionCode `175940518`, 81.035.948 bytes. SHA-256 `3099262121a201dcb2ae7e185bee6b2057287a5aeffae4be0e30f537e505e5b3`. Manifesto e checksum lidos localmente; nenhum APK/backup é incorporado ao repositório.

## Captura de colagem de texto implementada

`ClipboardKeyboard.D(Lfih;Z)V` chama `Lfkl.d(Context, Consumer, EditorInfo, String, String, pqu)` somente no ramo de texto. No DEX original há uma única chamada `invoke-static/range {v4 .. v9}`, com 24 registradores; o texto efetivamente despachado está em `v7`. O patch valida assinatura, chamada única, opcode e registradores antes de injetar. Reaplicação verifica quantidade e posição dos três hooks. O listener sintético compartilhado `Lfhz.onClick` não é alterado.

O corpo de `Lfkl.d` envia três eventos pelo Consumer: início de batch `-10090`, substituição de texto `-10141` e término de batch `-10090`. O Consumer `Levl`, case 18, encaminha a `Loxv.M(Lnur;)V`; `Lnvq.M` encaminha a `Lnvl.dD`. Essa cadeia não estabelece quando o editor remoto termina a inserção.

Por isso, a intenção captura apenas um snapshot elegível e o texto esperado. O retorno do dispatcher é um sinal para ler o editor, não confirmação suficiente. Tanto essa leitura quanto `onUpdateSelection` só admitem a transação se texto completo, faixa substituída e cursor coincidirem exatamente, na mesma geração/editor/InputConnection. Composição, seleção inesperada, extração parcial, mudança diferente, campo privado, troca de sessão e timeout cancelam a intenção. O timeout de 1.500 ms apenas descarta captura ainda pendente; nunca autoriza edição ou tolera divergência. Expirar uma intenção antiga não cancela a seguinte.

Qualquer tentativa de cartão limpa o journal anterior, inclusive imagens/URI. Somente o ramo textual pode criar um novo journal. Colagens acima do limite de 32.768 unidades UTF-16 continuam nativas e não entram no fallback local. O journal continua sendo de uma transação: uma segunda colagem substitui a primeira como operação reversível.

Uma colagem nativa comprovada usa diretamente a inversão local da faixa conhecida, sem despachar antes um Undo do host possivelmente assíncrono. Isso evita duas operações concorrentes sobre a mesma colagem. Redo reutiliza somente a transação efetivamente revertida. O código anterior de toolbar Paste/Cut e a delegação de histórico não capturado ao host são preservados.

## Descobertas de UI, ainda sem integração

| Binding estático | Evidência no APK | Limite atual |
| --- | --- | --- |
| Cabeçalho do clipboard | `res/crz.xml`, tag `.clipboard-accessory-body-top-bar`; label `0x7f0b01c8` dentro de FrameLayout `0x7f0b01c9`; ações no LinearLayout `0x7f0b0527` | Local de inserção identificado; lupa/campo ainda não instalados |
| Inicialização do cabeçalho | `ClipboardKeyboard.e` atribui o TextView `m` a `0x7f0b01c8`; `f` usa `0x7f0b01c7` | Há variantes de layout; não assumir que ambos os labels são o mesmo container |
| Faixa de favoritos | `res/HNz.xml`, `AccessPointsBar`, `suppressedSiblingId=0x7f0b158d` | O sibling é a área nativa de candidatos; não criar segunda barra |
| Layout da faixa | `res/N1t.xml` e `res/h_4.xml`: ManagedFrameLayout central; `res/Txf.xml` define SoftKeyView esquerda `0x7f0b053e` | Posição da view não prova o ActionDef ativo do botão dos quadradinhos |
| Abrir todos os atalhos | `Lmln.m(Lnur;)Z`, switch `-40005`, chega a `Lmlh.D()` | Não chamar esta rota para favoritos rápidos |
| Exibir favoritos nativos | `Lmln.M(ZZLpfz;Z)V` usa `Lmlh.w()` para obter tokens ordenados via `Lmku.c()`, resolve os `Lmic` e chama `Lmlh.C(List)` | Chamada exata identificada, mas flags/gestão de visibilidade precisam de validação de views |
| Ocultar faixa de APs | `Lmln.y(ZZ)V` → `Lmlh.y(ZZ)V`, que usa `Lpga.g` com ID `0x7f0b00a6` | Não equivale, por si só, a restaurar o estado de sugestões/composição |
| Entrada de eventos | `Lnvq.M(Lnur;)V` → `Lnvl.dD` | Interceptar globalmente este método sem provar restauração/foco não é uma integração segura de busca |

**A seta contextual e a pesquisa visual continuam não implementadas.** Não foi adicionada uma lupa que possa enviar a consulta ao app, nem aplicado um listener ao SoftKeyView apenas por sua posição. É necessário completar o binding do ActionDef/ícone e o roteamento isolado de entrada, além do filtro na apresentação sem alterar a lista usada por operações nativas de edição/arraste. Isso é trabalho pendente, não uma limitação resolvida pela existência de testes da máquina de estados.

## Validação e bloqueios

- 34 testes JVM de sessão, transação e descritores de spans passaram: os 26 anteriores desse conjunto mais 8 novos casos. Eles usam a lógica de produção e um InputConnection simulado, não cliques reais no Gboard.
- O runtime Android de edição e suas dependências reais de settings foram compilados com ECJ contra as APIs Android. Uma advertência de depreciação existente em `Configuration.locale` permanece.
- `git diff --check` passou. O catálogo inclui as três novas chamadas ABI e o arquivo consumidor; seu SHA-256 foi regenerado.
- Gradle 9.6.1 foi recuperado e executado localmente. `:patches:test` parou em `settings.gradle.kts:26`: plugin `app.morphe.patches:1.3.3` não resolvido, incluindo GitHub Packages. Não há credencial do registry nesta sessão.
- A compilação Kotlin/Gradle completa, transformação do APK, `.mpp` final, importação no Morphe, teste de spans Binder em dispositivo e UI física **não foram executados**. O novo hook não deve ser descrito como validado no aparelho.
- Nenhum workflow, merge ou release foi executado. Mantêm-se workflow exclusivamente manual, caches e publicação de `.mpp` somente em GitHub Releases, sem artifacts do Actions. Não há mudança de assinatura ou versão instalada.
