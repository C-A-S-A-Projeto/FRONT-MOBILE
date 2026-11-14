# Missão: Alinhar o backend Kotlin (app mobile) com a API Web (FastAPI)

## 1. Contexto

O projeto CASA tem dois lados que compartilham **o mesmo banco de dados**:

| Lado | Tecnologia | Local |
|------|-----------|-------|
| SaaS Web (fonte da verdade) | FastAPI (Python) | `E:\CASA\CASA-API` |
| App Mobile | Kotlin | (localize no workspace; se houver mais de um candidato, escolha o que contém o app Android/Kotlin principal e registre a escolha no relatório) |

Os dois já estão sendo aproximados manualmente. **O objetivo NÃO é clonar a API web dentro do Kotlin.** O objetivo é **encontrar divergências que podem causar problemas** (dados inconsistentes, campos faltando, formatos diferentes, regras diferentes) e **corrigi-las no lado Kotlin**, de forma que o que o app grava e lê no banco seja **estruturalmente idêntico** ao que a API web grava e lê.

> Princípio central: **a API web é a referência.** O Kotlin se adapta a ela, nunca o contrário (a menos que eu autorize explicitamente).

---

## 1.1 MODO AUTÔNOMO (leia primeiro)

Eu vou deixar você rodando sozinho e **não estarei disponível** para responder nada.

- **NÃO FAÇA PERGUNTAS. NÃO PARE PARA PEDIR CONFIRMAÇÃO.** Nenhuma. Em nenhum momento.
- Diante de qualquer dúvida ou ambiguidade, **decida sozinho** pela opção mais segura e coerente com a API web, **execute** e **registre a decisão e o motivo** no relatório.
- Existe **backup no Git**. Não tenha medo de falhas: se algo der errado, eu reverto. Não deixe de aplicar uma correção só por receio.
- Mesmo assim, **trabalhe com disciplina de Git** (veja seção 3, Fase 0) para que qualquer mudança seja fácil de reverter.
- Só encerre quando todas as fases estiverem concluídas e o relatório final estiver salvo. Se travar em algo, **contorne, registre o problema e siga para o próximo item**; nunca pare esperando resposta.

---

## 2. Regras inegociáveis

1. **NÃO DELETAR NADA.** Nenhum arquivo, classe, função, endpoint, campo ou coleção/tabela.
   - Se algo parece obsoleto ou duplicado: **marque** (comentário `// TODO(revisar): ...` ou item no relatório) e deixe como está.
   - Se precisar substituir um comportamento, mantenha o antigo como deprecado (`@Deprecated`) e crie o novo ao lado.
2. **NÃO ALTERAR A API WEB** (`E:\CASA\CASA-API`) — ela é somente leitura nesta tarefa. Se achar um bug nela, **reporte**, não corrija.
3. **NÃO ALTERAR O BANCO DIRETAMENTE** (nenhuma migração, script de escrita, limpeza de dados). Só propor.
4. **PRESERVAR as funções nativas do app.** Câmera/foto, gravação e envio de áudio, notificações, permissões, armazenamento local e qualquer outra capacidade exclusiva do mobile são o diferencial do app. Elas **não** têm equivalente na API web e **não** devem ser removidas, simplificadas ou "uniformizadas". O trabalho é garantir que o **resultado** delas (ex.: URL da foto, metadados do áudio) seja gravado no **mesmo formato** que a web usa.
5. **Mudanças pequenas e reversíveis.** Prefira vários commits pequenos a uma reescrita grande. Não refatore o que não tem relação com a divergência encontrada.
6. **Não inventar.** Se uma regra da API web não estiver clara, siga a interpretação mais provável com base no código, aplique e registre como "suposição" no relatório.
7. **Não expor segredos.** Não copie chaves, tokens, credenciais ou `.env` para relatórios ou código.

---

## 3. Fases de trabalho

Execute em ordem. **Não pule para a Fase 4 sem ter terminado e salvo o relatório das fases 1–3.**

### Fase 0 — Preparação (Git)

- Verifique o estado do repositório Git do projeto Kotlin. Se houver mudanças não commitadas, faça um commit de checkpoint (`chore: checkpoint antes do alinhamento com API web`).
- Crie e use uma branch própria, por exemplo `alinhamento-api-web`. Não trabalhe direto na branch principal.
- Durante a Fase 4, faça **um commit por divergência (ou grupo pequeno e coeso)**, com mensagem clara citando o ID (ex.: `fix(DIV-012): alinhar formato de data em Pedido com API web`).
- Não faça push, não faça merge, não reescreva histórico.

### Fase 1 — Mapear a API Web (fonte da verdade)

Leia `E:\CASA\CASA-API` e produza um inventário. Procure por `main.py`, routers, `models`, `schemas` (Pydantic), `services`, `crud`/repositórios, `dependencies`, middlewares, configuração de banco e autenticação.

Para **cada endpoint**, registre:
- Método + caminho (ex.: `POST /api/v1/...`)
- Autenticação/autorização exigida (tipo de token, papéis/permissões, escopo por usuário/organização/tenant)
- Parâmetros (path, query, body, headers) com **tipo, obrigatoriedade e valores padrão**
- Schema de request e de response (campos, tipos, nulabilidade, nomes exatos)
- Validações (tamanhos, regex, enums, ranges)
- Códigos de status e formato de erro retornado
- Regras de negócio (ex.: soft delete, campos calculados, ordenação padrão, paginação, filtros)
- Efeitos colaterais (o que grava, em quais coleções/tabelas)

Para **cada entidade/modelo de dados**, registre:
- Nome da coleção/tabela e nome exato de cada campo
- Tipo de cada campo, se é obrigatório, valor padrão
- Formato de IDs (UUID? string? int? gerado onde?)
- Formato de datas/horas (ISO 8601? timestamp? timezone? UTC?)
- Enums e seus valores exatos (maiúsculas/minúsculas importam)
- Relacionamentos e chaves estrangeiras/referências
- Campos de auditoria (`created_at`, `updated_at`, `deleted_at`, `owner_id`, etc.)
- Convenção de nomes (snake_case vs camelCase)

Também registre convenções gerais: paginação, formato de erro, versionamento, CORS, rate limit, uploads de arquivo.

### Fase 2 — Mapear o backend Kotlin

Faça o mesmo inventário no código Kotlin do app:
- Todos os pontos de acesso a dados (chamadas HTTP/Retrofit/Ktor, repositórios, DAOs, acesso direto ao banco, se houver)
- Modelos/DTOs/data classes e como são serializados (`@SerializedName`, `@SerialName`, `@Json`, nomes padrão, `naming strategy`)
- Autenticação e tratamento de sessão/token
- Tratamento de erros e códigos de status
- Cache/armazenamento local e como ele sincroniza com o servidor/banco
- Fluxos das **funções nativas** (foto, áudio, etc.): onde o arquivo é capturado, como é enviado, onde o resultado é gravado

Classifique cada item do Kotlin em: **(A)** tem equivalente na API web, **(B)** exclusivo do mobile (nativo), **(C)** existe no Kotlin mas parece não existir na web (reportar, não apagar).

### Fase 3 — Análise de divergências

Compare os dois inventários e liste **toda diferença que possa causar problema**. Categorias para verificar, no mínimo:

- **Estrutura de dados:** campos faltando, campos extras, nomes diferentes (`userId` vs `user_id`), tipos diferentes (int vs string, number vs boolean)
- **Nulabilidade e padrões:** campo obrigatório na web mas opcional no Kotlin (e vice-versa); valores padrão diferentes
- **Datas e horas:** formato, timezone, precisão, timestamp vs string
- **IDs:** formato, quem gera (cliente ou servidor), colisões possíveis
- **Enums e constantes:** valores diferentes, caixa diferente, valores que existem só de um lado
- **Validações:** regras da web que o Kotlin não aplica (pode gravar dado inválido no banco compartilhado)
- **Regras de negócio:** soft delete vs delete real, campos calculados, status/fluxos de estado, cascatas
- **Relacionamentos:** referências quebradas, vínculos que o Kotlin não grava
- **Autenticação/autorização/multi-tenant:** o Kotlin consegue ler/gravar dados que a web bloquearia? Escopo por usuário/organização é o mesmo?
- **Paginação, ordenação e filtros:** comportamento padrão diferente
- **Erros:** formatos e códigos de status diferentes que o app trata de forma incompatível
- **Uploads (foto/áudio):** caminho de armazenamento, nomenclatura, tipo/MIME, tamanho máximo, metadados gravados, URL final gravada no banco
- **Campos de auditoria:** `created_at`/`updated_at`/autor preenchidos de forma diferente ou não preenchidos
- **Endpoints/funcionalidades que existem na web e faltam no Kotlin**

Para cada divergência, registre:

| Campo | Conteúdo |
|-------|----------|
| ID | `DIV-001`, `DIV-002`... |
| Severidade | 🔴 Crítica (corrompe/inconsistência de dados) · 🟠 Alta (quebra funcionalidade) · 🟡 Média · 🟢 Baixa (padronização) |
| Onde na web | arquivo + linha/função |
| Onde no Kotlin | arquivo + linha/classe |
| Diferença | descrição objetiva |
| Risco | o que pode dar errado com o banco compartilhado |
| Correção proposta | mudança mínima, **sem deletar nada** |
| Afeta função nativa? | sim/não |

**Salve o relatório em `RELATORIO_DIVERGENCIAS.md` na raiz do projeto Kotlin** e prossiga direto para a Fase 4, sem esperar aprovação.

### Fase 4 — Correções no Kotlin

Só depois do relatório, aplique as correções, **começando pelas 🔴 e 🟠**. Para cada correção:

1. Faça a menor mudança possível que elimine a divergência.
2. Ao **organizar o existente**: renomeie campos serializados via anotação (mantendo o nome interno do Kotlin quando possível) em vez de renomear em todo o código; centralize constantes/enums duplicados; padronize formatadores de data em um único lugar.
3. Ao **criar o que falta**: adicione novos DTOs, endpoints, validações, campos e enums seguindo o padrão do projeto Kotlin já existente (mesma arquitetura, mesmo estilo de nomenclatura de arquivos).
4. Se uma mudança puder afetar dados já gravados pelo app (ex.: mudar formato de data/ID), **aplique mesmo assim** adotando o formato da API web para **gravação**, e implemente **leitura tolerante** para o formato antigo (aceitar os dois ao ler). Registre o impacto no relatório como "atenção: dados antigos".
5. Mantenha compatibilidade de leitura: o Kotlin deve conseguir **ler** dados antigos que já estão no banco, mesmo que passe a **gravar** no formato novo.
6. Garanta que as funções nativas continuam funcionando; ajuste apenas o ponto onde o resultado é persistido.

### Fase 5 — Verificação

- Compile/rode os testes do projeto Kotlin, se existirem. Se não compilar, **corrija sozinho**; se não conseguir corrigir após algumas tentativas, reverta apenas aquele commit (`git revert`), marque o item como ❌ com o motivo e siga em frente.
- Se possível, adicione testes simples de **contrato** (serialização/desserialização de cada DTO contra um exemplo de JSON gerado pelos schemas da API web).
- Atualize o `RELATORIO_DIVERGENCIAS.md` com o status de cada item: ✅ corrigido · ⚠️ corrigido com ressalvas (descrever) · ❌ não corrigido (motivo).

---

## 4. Banco de dados compartilhado

- Considere o banco como **compartilhado e em produção**: qualquer gravação divergente do app pode afetar o SaaS web.
- Descubra qual banco/serviço é usado lendo a configuração da API web (por exemplo Firebase/Firestore, SQL, etc.) e confirme que o app Kotlin aponta para **as mesmas** coleções/tabelas e **o mesmo** esquema de campos.
- Atenção especial a: nomes de coleções/tabelas, nomes de campos, tipos (ex.: `Timestamp` vs string), documentos com campos extras que o web ignora, e documentos que o web espera ter campos que o app não grava.
- Não rode nada que modifique dados reais durante a análise. Use apenas leitura de código.

---

## 5. Funções nativas do app (não mexer, só alinhar o resultado)

Trate como **intocáveis**:
- Captura de foto (câmera/galeria), compressão, rotação
- Gravação, reprodução e envio de áudio
- Permissões do Android, notificações, serviços em segundo plano
- Qualquer outro recurso que dependa do hardware ou da plataforma

Para cada uma, verifique **apenas**:
- O arquivo gerado é enviado/armazenado no **mesmo local e padrão de nome** que a web usa?
- O **registro no banco** que referencia o arquivo tem os **mesmos campos e formatos** que a web gravaria?
- Tipos MIME, extensões e limites de tamanho são compatíveis com o que a API web aceita/valida?
- A web consegue **exibir/reproduzir** o que o app gravou?

---

## 6. Formato da entrega final

Ao terminar, responda com:

1. **Resumo executivo** (máx. 10 linhas): quantas divergências, quantas corrigidas, quantas pendentes.
2. **Lista de arquivos criados/alterados** (nenhum deletado — confirme isso explicitamente).
3. **Decisões e suposições que você tomou sozinho**, com o motivo e como reverter cada uma.
4. **Problemas encontrados na API web** (somente reporte).
5. **Itens exclusivos do mobile** (categoria B) e **itens só do Kotlin** (categoria C) para eu revisar.
6. Link/caminho do `RELATORIO_DIVERGENCIAS.md`.

---

## 7. Como se comportar

- **Nunca pergunte nada.** Decida, execute e relate.
- Seja específico: sempre cite arquivo e linha/função, nunca "em algum lugar do código".
- Na dúvida, escolha a opção que **mais se aproxima da API web**, aplique, faça commit separado e registre.
- Não faça melhorias de estilo, performance ou arquitetura fora do escopo desta tarefa.
