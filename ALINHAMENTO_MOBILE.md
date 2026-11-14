# ALINHAMENTO MOBILE ↔ WEB — Projeto CASA

Você é um agente de engenharia sênior. Sua missão: **deixar a plataforma mobile em paridade funcional com a plataforma web**, usando o web como fonte da verdade.

---

## 1. Contexto

O projeto CASA é uma plataforma que **configura um dispositivo de hardware baseado em ESP32**. O ecossistema tem 4 partes:

| Parte | Caminho | Status | Permissão |
|---|---|---|---|
| Frontend Web | `E:\CASA\ProjetoCasa` | Pronto, testado e aprovado | **SOMENTE LEITURA** |
| API Web | `E:\CASA\CASA-API` | Pronta, testada e aprovada | **SOMENTE LEITURA** |
| Frontend Mobile | `E:\CASA\FRONT-MOBILE` (diretório atual) | Em andamento, incompleto | **LEITURA E ESCRITA** |
| Firmware ESP32 | `E:\CASA\casa_firmware_v8\casa_firmware_v8.ino` | Referência | **SOMENTE LEITURA** |

> O app mobile possui sua própria API/camada de backend (confirme onde fica ao explorar o repositório). O dono acredita que ela está 100% ou próxima disso, mas **isso precisa ser verificado, não assumido**.

### Problema a resolver
Muitas funcionalidades existem no backend, mas **não aparecem no front mobile** por falta de telas, rotas, componentes ou chamadas. De nada adianta ter função no backend sem como usá-la na interface.

---

## 2. Regras invioláveis

1. **NÃO altere nenhum arquivo** em `E:\CASA\ProjetoCasa`, `E:\CASA\CASA-API` ou `E:\CASA\casa_firmware_v8`. Apenas leia e analise. Não rode comandos que gerem arquivos nesses diretórios (sem `npm install`, build, formatadores, `git checkout`, etc. lá).
2. Todas as alterações acontecem **apenas** em `E:\CASA\FRONT-MOBILE`.
3. O web é a referência de comportamento, regras de negócio, contratos de API e fluxos. O mobile deve se adaptar **à experiência mobile** (navegação, gestos, layout), sem copiar mecanicamente UI de desktop.
4. **Não invente** endpoints, campos ou regras. Tudo deve ser rastreável a código real do web, da API ou do firmware.
5. **Nunca** commite segredos (`.env`, chaves, tokens, senhas, keystores). Verifique o `.gitignore`.
6. Não quebre o que já funciona no mobile. Mudanças devem ser incrementais e verificáveis.
7. Se encontrar uma ambiguidade que muda o comportamento de forma significativa, registre a decisão tomada no relatório final (seção 7) em vez de parar.

---

## 3. Fase 1 — Descoberta (somente leitura)

Faça o inventário completo antes de escrever qualquer código.

### 3.1 Mobile (`E:\CASA\FRONT-MOBILE`)
- Identifique stack, framework, gerenciador de pacotes, estrutura de pastas, navegação/rotas, camada de serviços/HTTP, gerenciamento de estado, autenticação, tratamento de erros, i18n, tema.
- Leia `package.json` (ou equivalente), README, configs e scripts disponíveis.
- Localize a API do mobile (se estiver no repositório ou referenciada) e liste todos os endpoints expostos.
- Liste todas as telas/rotas existentes e quais endpoints cada uma consome.

### 3.2 Web (`E:\CASA\ProjetoCasa`)
- Liste todas as páginas/rotas, componentes principais, formulários, fluxos de usuário, permissões/perfis, estados (loading, vazio, erro), validações e mensagens.
- Para cada tela, registre: objetivo, endpoints consumidos, campos exibidos/editáveis, ações disponíveis.

### 3.3 API Web (`E:\CASA\CASA-API`)
- Liste **todos** os endpoints: método, rota, autenticação/autorização, payload de entrada, resposta, códigos de erro, validações, regras de negócio.
- Identifique modelos/entidades, enums, constantes e fluxos (ex.: provisionamento, configuração do dispositivo, status, comandos).

### 3.4 Firmware (`E:\CASA\casa_firmware_v8\casa_firmware_v8.ino`)
- Entenda **como o dispositivo é configurado**: protocolo (BLE, Wi-Fi AP/portal, HTTP, MQTT, serial etc.), parâmetros aceitos, formatos de payload, comandos, estados, tópicos/endpoints do próprio dispositivo, fluxo de pareamento/provisionamento.
- Compare o que o firmware espera/expõe com o que o web faz e com o que o mobile faz. Aponte qualquer divergência de contrato (nomes de campos, formatos, tipos, ordem de passos, timeouts).

---

## 4. Fase 2 — Matriz de desalinhamento

Crie o arquivo `E:\CASA\FRONT-MOBILE\ALINHAMENTO_RELATORIO.md` com:

### 4.1 Paridade de funcionalidades
Tabela com uma linha por funcionalidade/tela do web:

| Funcionalidade | Web (rota/componente) | Endpoint(s) | API Mobile tem? | Front Mobile tem? | Gap | Prioridade |
|---|---|---|---|---|---|---|

### 4.2 Paridade de API
Compare API Web × API Mobile: endpoints ausentes, payloads divergentes, regras de validação diferentes, autenticação diferente, códigos de erro diferentes.

### 4.3 Paridade de contrato com o firmware
Liste qualquer ponto em que o fluxo de configuração do mobile diverge do web ou do que o firmware realmente espera.

### 4.4 Código morto / inutilizável
Funções ou endpoints do backend mobile sem nenhuma rota/tela que os consuma.

### Critério de prioridade
- **P0**: quebra a configuração do dispositivo ou o fluxo principal.
- **P1**: funcionalidade do web totalmente ausente no mobile.
- **P2**: funcionalidade parcial ou comportamento divergente.
- **P3**: polimento, textos, estados vazios, consistência visual.

---

## 5. Fase 3 — Implementação

Trabalhe por prioridade (P0 → P3), em passos pequenos.

1. **Primeiro a API mobile**, se algo estiver faltando ou divergente: corrija para igualar contratos e regras do web. Se estiver correta, não mexa.
2. **Depois o front mobile**: crie as telas, rotas, navegação, componentes, serviços de chamada, estados (loading/vazio/erro/sucesso), validações e mensagens equivalentes ao web.
3. Toda função de backend relevante deve ficar **alcançável pela interface** (navegação, botão, menu ou fluxo).
4. Siga rigorosamente os padrões já existentes no mobile (estrutura de pastas, nomenclatura, estilo de código, componentes reutilizáveis). Reutilize antes de criar.
5. Adapte a UX para mobile: áreas de toque adequadas, teclado, safe areas, pull-to-refresh, estados offline/erro de rede quando fizer sentido.
6. Mantenha o fluxo de configuração do ESP32 fiel ao que o firmware realmente implementa.
7. Após cada bloco coeso de mudanças: rode lint, typecheck e build (conforme scripts do projeto) e corrija o que quebrar.
8. Atualize `ALINHAMENTO_RELATORIO.md` marcando cada gap como ✅ resolvido, ⚠️ parcial ou ❌ não resolvido (com motivo).

---

## 6. Fase 4 — Testes (Cypress) e entrega

### 6.1 Cypress
1. Verifique se o Cypress já está configurado no mobile (`cypress.config.*`, pasta `cypress/`). Se não estiver, instale e configure seguindo as convenções do projeto.
2. Crie/atualize testes cobrindo as funcionalidades adicionadas ou alinhadas nesta tarefa (fluxos principais, navegação, formulários, estados de erro).
3. Rode a suíte completa em modo headless (`npx cypress run` ou o script do projeto).
4. Se algo falhar: investigue a causa raiz, corrija o código (ou o teste, se o teste estiver errado) e rode de novo. **Repita até a suíte passar.** Não desabilite nem pule testes para "passar".

### 6.2 Git — verificação e push
Execute nesta ordem, dentro de `E:\CASA\FRONT-MOBILE`:

1. `git status` — revise tudo que mudou; confirme que **nenhum segredo ou arquivo gerado indevido** será commitado.
2. Faça commits **lógicos e atômicos** com mensagens claras (ex.: `feat(mobile): tela de X alinhada ao web`, `fix(api): ajusta payload de Y`, `test(cypress): cobre fluxo Z`).
3. `git fetch origin` e verifique se há **commits novos na remota**:
   - `git log HEAD..origin/main --oneline`
4. **Se houver commits novos na `main` remota**:
   - Rode `git pull --rebase origin main`.
   - Resolva conflitos preservando as duas intenções; em caso de dúvida real, registre no relatório.
   - **Rode o Cypress novamente** (e lint/build) após o rebase. Só prossiga se passar.
5. **Se não houver commits novos**, siga direto.
6. Confirme que está na branch `main` (`git branch --show-current`) e faça `git push origin main`.
7. **Nunca** use `--force` ou `--force-with-lease` na `main`. Se o push for rejeitado, repita o fetch/rebase/teste.
8. Confirme o push com `git log origin/main -1` e `git status`.

> Se o Cypress não passar após esforço razoável de correção, **não faça push**. Explique o que falhou no relatório.

---

## 7. Entregável final

Ao terminar, responda com um resumo curto contendo:

- O que foi analisado (web, API web, firmware, mobile).
- Principais desalinhamentos encontrados e o que foi corrigido.
- O que ficou pendente e por quê.
- Decisões tomadas em pontos ambíguos.
- Resultado do Cypress (passaram/falharam, quantidade).
- Se havia commits novos na remota e como foram tratados.
- Hash do último commit enviado e confirmação do push na `main`.

O relatório detalhado fica em `E:\CASA\FRONT-MOBILE\ALINHAMENTO_RELATORIO.md`.

---

## 8. Checklist de execução

- [ ] Li e mapeei mobile, web, API web e firmware (sem alterar web/API/firmware)
- [ ] Gerei a matriz de desalinhamento em `ALINHAMENTO_RELATORIO.md`
- [ ] Verifiquei a API mobile e corrigi divergências (se houver)
- [ ] Implementei telas, rotas e fluxos faltantes no front mobile
- [ ] Todo endpoint relevante é alcançável pela interface
- [ ] Fluxo de configuração do ESP32 condiz com o firmware
- [ ] Lint, typecheck e build passando
- [ ] Cypress criado/atualizado e **passando**
- [ ] Verifiquei commits novos na remota e tratei (rebase + retestar)
- [ ] Sem segredos commitados
- [ ] Push na `main` feito sem force
- [ ] Resumo final entregue
