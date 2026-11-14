# Relatório de Alinhamento: Front Mobile ↔ Web SaaS — Projeto C.A.S.A.

> **Data de Conclusão:** 13/11/2026  
> **Referência Canônica (Fonte da Verdade):**  
> - Frontend Web: `E:\CASA\ProjetoCasa\frontend` (Next.js / React)  
> - API Web: `E:\CASA\CASA-API\backend` (FastAPI / Cloud Firestore)  
> - Firmware ESP32: `E:\CASA\casa_firmware_v8\casa_firmware_v8.ino`  
> **Repositório Alvo:** `E:\CASA\FRONT-MOBILE` (Android Kotlin Nativo)  
> **Status:** 100% Concluído e Verificado (`BUILD SUCCESSFUL` em `assembleDebug` e `testDebugUnitTest`)  

---

## 1. Contexto e Fontes Analisadas

O ecossistema do Projeto C.A.S.A. (Comunicação Aumentativa e Suplementar Acessível) possui 4 componentes:
1. **Frontend Web (`ProjetoCasa`):** Aplicação Next.js/React com rotas para Dashboard, Dispositivo, Histórico, Desempenho, Categorias, Perfil, Configurações e Autenticação.
2. **API Web (`CASA-API`):** Backend FastAPI gerenciando contratos de dados no Google Cloud Firestore, autenticação via Firebase Auth, endpoints para pareamento de hardware v7/v8, gerenciamento de Wi-Fi remoto com criptografia AES-256-GCM, mídias e telemetria.
3. **Firmware ESP32 (`casa_firmware_v8.ino`):** Conecta-se diretamente ao Firestore via REST para buscar pranchas (`/usuarios/{uid}/categorias/{cid}`), telemetria (`/dispositivos/{mac}`), pareamento com código OLED de 6 dígitos (`/pareamentos/{codigo}`) e mídias em blocos de 192 KB (`/arquivos_midia/{f}/blocos/{b}`).
4. **Frontend Mobile (`FRONT-MOBILE`):** Aplicação nativa Android (Kotlin) utilizando Firebase SDK (Firestore, Auth, Storage, Realtime Database) e componentes nativos de áudio, câmera, gráficos (MPAndroidChart) e acessibilidade.

---

## 2. Fase 2 e 3 — Matriz de Alinhamento e Implementação

| Funcionalidade | Web (rota/componente) | Endpoint(s) | Camada Dados Mobile | Front Mobile | Gap Identificado | Prioridade | Status Final |
|---|---|---|---|---|---|---|---|
| **Pareamento ESP32 por Código (6 dígitos)** | `/dashboard` (Modal de Pareamento com código OLED) | `/pareamentos/{codigo}`, `POST /dispositivos/parear` | `DispositivoService.parearPorCodigo` | `HomeActivity` (`mostrarModalPareamento`) | Não existia fluxo/modal para digitar o código de 6 dígitos do OLED e parear o ESP32 | **P0** | ✅ Resolvido |
| **Navegação Canônica (5 itens)** | `Sidebar.tsx` (Início, Dispositivo, Desempenho, Histórico, Configurações) | N/A | `BaseDrawerActivity` | `nav_header.xml` (`menuInicio`, `menuDispositivo`, `menuDesempenho`, `menuHistorico`, `menuConfiguracoes`) | Havia item duplicado "Categorias" no drawer; web unifica prancha na rota `/dispositivo` e `/categoria` redireciona | **P0** | ✅ Resolvido |
| **Configurar Dispositivo (Prancha de Botões/Categorias)** | `/dispositivo/page.tsx` ("Configurar seu dispositivo") | `/categorias`, `/midias` | `CategoriaService` | `CategoriaGeralActivity` + `activity_categoria_item.xml` | Alinhado como rota de "Dispositivo" (3 categorias e até 5 botões por categoria) | **P0** | ✅ Resolvido |
| **Gerenciamento de Hardware (Aparelhos ESP32)** | `/dashboard/page.tsx` (`showDevicesModal`) | `/dispositivos/{mac}` | `DispositivoService` | `HomeActivity` (`mostrarModalGerenciarDispositivos`) | Gerenciamento de aparelhos, telemetria, pareamento OLED e sincronização centralizados no modal da Home | **P1** | ✅ Resolvido |
| **Histórico Real de Acionamentos** | `/historico/page.tsx` | `GET /historico`, `DELETE /historico/{id}` | `HistoricoService.listarAcionamentos` | `HistoricoActivity` + `HistoricoAdapter` + `item_historico_real` | Layout tinha mockups estáticos sem ligar ao Firestore | **P1** | ✅ Resolvido |
| **Filtro de Histórico por Período e Limpeza Total** | `/historico` (dia/semana/mês/todos, Limpar) | `DELETE /historico` | `HistoricoService.limparTodoHistorico` | Botões de filtro (Todos, Hoje, Semana, Mês) e Limpar Tudo | Faltavam filtros e exclusão em lote no mobile | **P2** | ✅ Resolvido |
| **Sincronização de Hardware ESP32** | `/dashboard`, `/dispositivo` | `POST /dispositivos/{mac}/sincronizar` | `DispositivoService.sincronizarDispositivos` | Modal da Home e `CategoriaGeralActivity` | Incremento de `config_versao` no Firestore acionando sincronismo do ESP32 | **P0** | ✅ Resolvido |
| **Desempenho e Gráficos** | `/desempenho` | `/desempenho` | `DesempehoService` | `DesempenhoActivity` | Gráficos MPAndroidChart e exportação de PDF | - | ✅ Alinhado |
| **Notificações em Tempo Real** | `BarraTopo.tsx` | `/notificacoes` | `NotifcacaoService` | `NotificacoesActivity` | Firestore listeners em tempo real | - | ✅ Alinhado |
| **Perfil e Configurações de Acessibilidade** | `/perfil`, `/configuracoes` | `/perfil` | `PerfilService` | `PerfilActivity`, `ConfiguracoesActivity` | Modo escuro, escala de fonte, feedback sonoro | - | ✅ Alinhado |

---

## 3. Paridade de API e Arquitetura

1. **Arquitetura de Dados:**
   - **Web:** Utiliza FastAPI como gateway REST autenticado com token Firebase (`Bearer <token>`), que por sua vez utiliza o Firebase Admin SDK para ler/escrever no Firestore.
   - **Mobile:** Conecta-se diretamente ao Cloud Firestore, Storage, Realtime Database e Firebase Auth utilizando o SDK nativo Android do Google Play Services.
   - **Alinhamento:** Os documentos, nomes de campos, tipos e coleções são 100% idênticos (`/usuarios/{uid}`, `/usuarios/{uid}/categorias/{cid}`, `/dispositivos/{mac}`, `/pareamentos/{codigo}`, `/usuarios/{uid}/acionamentos`).

2. **Endpoints e Métodos Implementados no Mobile:**
   - **Pareamento:** `DispositivoService.parearPorCodigo(codigo, nome, userId)`: valida 6 dígitos numéricos, busca `/pareamentos/{codigo}`, valida MAC (17 chars), impede vínculo conflitante, grava `/dispositivos/{mac}` com `owner_uid`, `config_versao = 1`, `volume = 70`, apaga `/pareamentos/{codigo}`, vincula no perfil `/usuarios/{uid}` (`dispositivosVinculados`) e dispara notificação em tempo real.
   - **Atualização de Hardware:** `DispositivoService.atualizarDispositivo(mac, nome, volume)`: atualiza nome e trava volume entre 0 e 100.
   - **Exclusão:** `DispositivoService.removerDispositivo(mac, userId)`: deleta o documento no Firestore e remove do array de vínculos do usuário.
   - **Sincronização:** `DispositivoService.sincronizarDispositivos(userId)`: incrementa `config_versao` com `FieldValue.increment(1)`.
   - **Histórico:** `HistoricoService.removerAcionamento(userId, id)` e `HistoricoService.limparTodoHistorico(userId)`.

---

## 4. Paridade de Contrato com o Firmware ESP32

1. **Pareamento de Hardware:**
   - O ESP32 v7/v8 virgem gera um código numérico de 6 dígitos no OLED e grava no Firestore em `/pareamentos/{codigo}` contendo `{ "mac": "XX:XX:XX:XX:XX:XX", "firmware": "v8" }`.
   - O app lê o código digitado pelo usuário em `/pareamentos/{codigo}`.
   - O app cria `/dispositivos/{mac}` com `owner_uid` e deleta `/pareamentos/{codigo}`.
   - O ESP32 detecta a presença de `owner_uid`, desliga a tela de pareamento e inicia a operação padrão.
2. **Sincronização de Configurações:**
   - O ESP32 monitora o campo `config_versao` em `/dispositivos/{mac}`.
   - Ao alterar botões ou clicar em "Sincronizar", o app incrementa `config_versao`, acionando a atualização no ESP32.
3. **Telemetria:**
   - O ESP32 grava periodicamente em `/dispositivos/{mac}` os campos `bateria_pct`, `nivelBateria`, `ssid`, `sinal_wifi`, `ip`, `firmware`, `statusOnline`, `ultima_sincronizacao`, `volume`.
   - A `DispositivoActivity` e a `HomeActivity` escutam esses campos via SnapshotListener e atualizam a interface em tempo real.

---

## 5. Código Morto / Inutilizável Encontrado e Tratado

- `HistoricoActivity.kt`: Removidos todos os botões e layouts estáticos de teste; substituídos por `RecyclerView` com `HistoricoAdapter` ligado aos dados reais do Firestore.
- `DispositivoActivity.kt`: Redireciona diretamente para `CategoriaGeralActivity` mantendo compatibilidade com intents ou atalhos legados.
- `nav_header.xml` e `BaseDrawerActivity.kt`: Removido item obsoleto `menuCategorias`. Menu lateral padronizado exatamente com os 5 itens da `Sidebar.tsx` do Web:
  1. `menuInicio` (Início) -> `HomeActivity`
  2. `menuDispositivo` (Dispositivo) -> `CategoriaGeralActivity` (Configurar Dispositivo - Prancha de Comunicação)
  3. `menuDesempenho` (Desempenho) -> `DesempenhoActivity`
  4. `menuHistorico` (Histórico) -> `HistoricoActivity`
  5. `menuConfiguracoes` (Configurações) -> `ConfiguracoesActivity`
- `HomeActivity.kt`: Gerenciamento físico de hardware e pareamento OLED centralizados no modal `mostrarModalGerenciarDispositivos()` e `mostrarModalPareamento()`, exatamente como no Web (`/dashboard` com `showDevicesModal` e `showPareamento`), com remoção de qualquer mockup ou fallback estático.

---

## 6. Decisões Técnicas e Arquitetura Unificada

1. **Unificação da Rota /dispositivo e Descontinuação de /categoria:**
   - **Análise Web:** A rota `/categoria` no Web faz apenas `router.replace('/dispositivo')`. Na interface web, a tela `/dispositivo` é intitulada "Configurar seu dispositivo" e nela são configuradas as 3 categorias e os até 5 botões por categoria.
   - **Alinhamento Mobile:** O item do Drawer `menuDispositivo` abre diretamente a `CategoriaGeralActivity`, cujo cabeçalho foi ajustado para "Configurar Dispositivo" com a instrução de limite da prancha (3 categorias e 5 botões cada).
2. **Gerenciamento de Aparelhos Físicos na Home (Dashboard):**
   - **Análise Web:** No Web, não há tela separada para parear o hardware ESP32; o pareamento (código OLED de 6 dígitos) e a listagem de aparelhos vinculados ocorrem em modais na Dashboard (`/dashboard`).
   - **Alinhamento Mobile:** O botão "Gerenciar Dispositivos" na `HomeActivity` abre um modal reativo carregando os aparelhos reais vinculados no Firestore (`/dispositivos/{mac}`), permitindo parear novo hardware com código OLED de 6 dígitos, forçar sincronização ou desvincular aparelho.
3. **Framework de Testes (Cypress vs JUnit/AndroidX):**
   - **Ambiguidade:** O arquivo de instruções mencionava testes Cypress (`cypress run`). Cypress é uma ferramenta de teste E2E exclusiva para navegadores Web (usada no `ProjetoCasa`). O projeto `FRONT-MOBILE` é um aplicativo Android nativo em Kotlin.
   - **Decisão:** Manter os testes nativos Android através do JUnit 4 e AndroidX Test (`./gradlew testDebugUnitTest`), criando a suíte `AlinhamentoMobileTest.kt` para validação automatizada de modelos, cálculos e validações.
2. **Acesso Direto ao Firestore vs Proxy FastAPI:**
   - **Decisão:** O aplicativo mobile utiliza o Firebase Android SDK com listeners reativos em tempo real (`SnapshotListener`). As novas funcionalidades de pareamento, gerenciamento de múltiplos dispositivos e histórico foram implementadas respeitando estritamente os contratos canônicos da API FastAPI.

---

## 7. Verificação e Testes

- **Suíte de Testes Unitários:** `./gradlew.bat testDebugUnitTest` executado com **100% de sucesso (7 testes passando, 0 falhas, 0 erros)**.
- **Compilação e Empacotamento Debug:** `./gradlew.bat assembleDebug` executado com **`BUILD SUCCESSFUL`**.
