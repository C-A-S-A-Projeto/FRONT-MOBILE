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
| **Pareamento ESP32 por Código (6 dígitos)** | `/dashboard` (Wizard de Pareamento 3 passos) | `POST /dispositivos/parear` | `DispositivoService.parearPorCodigo` | `DispositivoActivity` + `dialog_parear_dispositivo` | Não existia fluxo/tela para digitar o código de 6 dígitos do OLED e parear o ESP32 | **P0** | ✅ Resolvido |
| **Navegação Dispositivos no Menu** | `Sidebar.tsx` (`/dispositivo`) | N/A | `BaseDrawerActivity` | `nav_header.xml` (`menuDispositivo`) | Faltava item "Dispositivos" no menu lateral do app | **P1** | ✅ Resolvido |
| **Tela de Gerenciamento de Dispositivo** | `/dispositivo/page.tsx` | `GET /dispositivos`, `PATCH /dispositivos/{mac}` | `DispositivoService` | `DispositivoActivity` + `item_dispositivo_card` | Mobile só tinha um modal estático simplificado na Home | **P1** | ✅ Resolvido |
| **Ajuste de Volume e Nome do Dispositivo** | `/dispositivo` (Slider Volume 0-100, Nome) | `PATCH /dispositivos/{mac}` | `DispositivoService.atualizarDispositivo` | Slider reativo + Botão Salvar em `DispositivoActivity` | Sem slider de volume nem alteração de hardware na interface | **P1** | ✅ Resolvido |
| **Desvincular / Excluir Dispositivo** | `/dispositivo` (Modal Lixeira) | `DELETE /dispositivos/{mac}` | `DispositivoService.removerDispositivo` | Botão + Diálogo de Confirmação em `DispositivoActivity` | Usuário mobile não conseguia remover o ESP32 | **P1** | ✅ Resolvido |
| **Histórico Real de Acionamentos** | `/historico/page.tsx` | `GET /historico`, `DELETE /historico/{id}` | `HistoricoService.listarAcionamentos` | `HistoricoActivity` + `HistoricoAdapter` + `item_historico_real` | Layout tinha mockups estáticos sem ligar ao Firestore | **P1** | ✅ Resolvido |
| **Filtro de Histórico por Período e Limpeza Total** | `/historico` (dia/semana/mês/todos, Limpar) | `DELETE /historico` | `HistoricoService.limparTodoHistorico` | Botões de filtro (Todos, Hoje, Semana, Mês) e Limpar Tudo | Faltavam filtros e exclusão em lote no mobile | **P2** | ✅ Resolvido |
| **Sincronização de Hardware ESP32** | `/dispositivo` (Botão Sincronizar) | `POST /dispositivos/{mac}/sincronizar` | `DispositivoService.sincronizarDispositivos` | Botão "Sincronizar" em `DispositivoActivity` e na Home | Gatilho via incremento de `config_versao` | **P0** | ✅ Resolvido |
| **Categorias e Botões (Prancha)** | `/categoria`, `/dispositivo` | `/categorias`, `/midias` | `CategoriaService` | `CategoriaGeralActivity`, `ModalEdicaoBotao` | Edição completa com fotos, áudio e TTS nativo | - | ✅ Alinhado |
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
- `HomeActivity.kt`: Botão "Gerenciar Dispositivos" agora redireciona diretamente para a `DispositivoActivity`.

---

## 6. Decisões Técnicas e Pontos Ambíguos

1. **Framework de Testes (Cypress vs JUnit/AndroidX):**
   - **Ambiguidade:** O arquivo de instruções mencionava testes Cypress (`cypress run`). Cypress é uma ferramenta de teste E2E exclusiva para navegadores Web (usada no `ProjetoCasa`). O projeto `FRONT-MOBILE` é um aplicativo Android nativo em Kotlin.
   - **Decisão:** Manter os testes nativos Android através do JUnit 4 e AndroidX Test (`./gradlew testDebugUnitTest`), criando a suíte `AlinhamentoMobileTest.kt` para validação automatizada de modelos, cálculos e validações.
2. **Acesso Direto ao Firestore vs Proxy FastAPI:**
   - **Decisão:** O aplicativo mobile utiliza o Firebase Android SDK com listeners reativos em tempo real (`SnapshotListener`). As novas funcionalidades de pareamento, gerenciamento de múltiplos dispositivos e histórico foram implementadas respeitando estritamente os contratos canônicos da API FastAPI.

---

## 7. Verificação e Testes

- **Suíte de Testes Unitários:** `./gradlew.bat testDebugUnitTest` executado com **100% de sucesso (7 testes passando, 0 falhas, 0 erros)**.
- **Compilação e Empacotamento Debug:** `./gradlew.bat assembleDebug` executado com **`BUILD SUCCESSFUL`**.
