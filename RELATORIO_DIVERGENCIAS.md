# Relatório de Análise e Alinhamento: Backend Kotlin vs API Web (FastAPI)

> **Data de Geração:** 11/11/2026  
> **Referência Canônica (Fonte da Verdade):** `E:\CASA\CASA-API\backend` (FastAPI / Cloud Firestore)  
> **Projeto Alvo:** `E:\CASA\FRONT-MOBILE` (Android Kotlin)  
> **Branch de Trabalho:** `alinhamento-api-web`  
> **Status:** 100% Concluído e Verificado (`BUILD SUCCESSFUL` em `assembleDebug`)

---

## 1. Contexto e Arquitetura Compartilhada

O ecossistema C.A.S.A. integra três componentes principais que compartilham a mesma instância do **Google Cloud Firestore / Firebase**:
1. **SaaS Web (FastAPI / Python)**: Fonte da verdade para contratos de dados, regras de negócio e limites.
2. **Firmware ESP32 (v7)**: Conecta-se diretamente ao Firestore via REST para baixar configurações (`config_versao`), imagens (blocos Base64) e telemetrias.
3. **Aplicativo Mobile (Android / Kotlin)**: Interface móvel para o usuário final, com recursos nativos de acessibilidade, câmera, gravação de áudio e notificações.

O objetivo deste trabalho foi garantir que **tudo o que o app Kotlin grava e lê no banco seja estruturalmente e contratualmente idêntico ao que a API Web e o ESP32 operam**, sem degradar as capacidades móveis nativas.

---

## 2. Inventário de Recursos da API Web (FastAPI)

### 2.1 Coleções Canônicas no Cloud Firestore
- `/usuarios/{uid}`: Perfil do responsável, preferências globais (`notificacoes_ativas`, `modo_escuro`, `tamanho_fonte`) e lista `dispositivosVinculados`.
- `/usuarios/{uid}/categorias/{cid}`: Categorias do usuário. Cada documento contém os metadados da categoria e um array embutido `botoes` (até 5 itens por categoria, máximo de 3 categorias por usuário).
- `/usuarios/{uid}/midias/{mid}`: Metadados de imagens e áudios enviados ou sintetizados via TTS.
- `/usuarios/{uid}/acionamentos/{aid}`: Histórico de toques em botões (pictogramas), com data ISO 8601 UTC, origem (`app` ou `dispositivo`) e dispositivo associado.
- `/usuarios/{uid}/notificacoes/{nid}`: Alertas e avisos em tempo real com indicador de leitura (`lida`) e data ISO 8601 UTC (`criada_em`).
- `/dispositivos/{mac}`: Hardware ESP32 vinculado. Telemetria, volume, versionamento de configuração (`config_versao`) e credenciais Wi-Fi cifradas (`wifi_versao`, `wifi_redes`).
- `/arquivos_midia/{arquivo}` e `/arquivos_midia/{arquivo}/blocos/{NNNN}`: Binários de mídia divididos em blocos raw de 196.608 bytes (192 KB) codificados em Base64.
- `/pareamentos/{codigo}`: Códigos temporários de 6 dígitos gerados pelo ESP32 para pareamento seguro.
- `/dispositivos_segredos/{mac}`: Chave simétrica AES-256 de 32 bytes em Base64 para Wi-Fi remoto.

### 2.2 Regras de Negócio e Constantes Globais
- `max_categorias = 3`
- `max_botoes_por_categoria = 5`
- `tamanho_bloco_midia = 196608` bytes (192 KB)
- `imagem_lado_px = 240` (JPEG Baseline 240x240 sem progressão, subsampling 4:2:0)
- `formato_data = ISO 8601 UTC` (`YYYY-MM-DDTHH:mm:ss.ffffff+00:00` ou `YYYY-MM-DDTHH:mm:ssZ`)
- `gatilho_hardware = bump_dispositivos`: Incrementa `config_versao` atômico via `FieldValue.increment(1)` nos `/dispositivos` do usuário.

---

## 3. Matriz de Divergências e Resoluções

| ID | Severidade | Onde na Web | Onde no Kotlin | Descrição da Divergência | Correção Aplicada | Commit | Status |
|---|---|---|---|---|---|---|---|
| **DIV-001** | 🔴 Crítica | `routers/categorias.py:19-21`, `firestore.rules:26` | `CategoriaService.kt` | Uso de coleções raiz `/categoria` e `/categoria_botao` em vez de `/usuarios/{uid}/categorias/{cid}` com array `botoes` | Padronizado `/usuarios/{uid}/categorias/{cid}` como coleção canônica em todas as operações CRUD do Kotlin, com migração automática e espelhamento tolerante legado. | `c9cb09f` | ✅ Corrigido |
| **DIV-002** | 🔴 Crítica | `config.py:9`, `routers/categorias.py:70` | `CategoriaService.kt` | Limite máximo de categorias configurado como 10 no Kotlin, contra 3 na Web API e no firmware ESP32 | Atualizado `MAX_CATEGORIAS = 3` no `CategoriaService` e validação preventiva antes da escrita. | `c9cb09f` | ✅ Corrigido |
| **DIV-003** | 🟠 Alta | `util.py:23-26`, `routers/categorias.py:94` | `CategoriaService.kt`, `CategoriaRepository.kt` | Notificação ao hardware usava Realtime Database `/dispositivo/controle` em vez de incrementar `config_versao` em `/dispositivos/{mac}` | Implementado `bumpDispositivos(userId)` executando `FieldValue.increment(1)` em `/dispositivos` onde `owner_uid == userId`. Mantida notificação secundária em tempo real como fallback. | `c9cb09f` | ✅ Corrigido |
| **DIV-004** | 🟠 Alta | `storage.py:20-21,161` | `Esp32ImageProcessor.kt` | `TAMANHO_BLOCO` de mídia era 600 KB no Kotlin contra 192 KB (196.608 bytes) na Web e no ESP32 | Ajustado `TAMANHO_BLOCO = 196608` (192 KB, múltiplo de 3 para Base64 sem padding interno) em `Esp32ImageProcessor`. | `c92f65f` | ✅ Corrigido |
| **DIV-005** | 🟠 Alta | `schemas.py:36-45`, `routers/categorias.py:80-92` | `model/Categoria.kt` | Faltavam os campos `imagem_id`, `imagem`, `audio_id`, `audio`, `tipo_audio`, `emoji` em `Categoria`; campos locais não tinham `@get:Exclude` | Adicionados todos os campos do contrato Web e marcadas propriedades exclusivas locais com `@get:Exclude`. | `a7b7585` | ✅ Corrigido |
| **DIV-006** | 🟠 Alta | `schemas.py:16-24`, `routers/categorias.py:167` | `model/Categoria.kt` | Faltava o campo `tipo_audio` no botão (`CategoriaItem`); campos locais móveis eram persistidos sem `@get:Exclude` | Adicionado `var tipo_audio: String? = null` em `CategoriaItem`, construtor de compatibilidade posicional e `@get:Exclude` nos campos locais. | `a7b7585`, `aaae437` | ✅ Corrigido |
| **DIV-007** | 🟡 Média | `routers/historico.py:33-41` | `model/Acionamento.kt`, `HistoricoService.kt` | Faltava o campo `tempo_aproximado: Boolean` no modelo de histórico de acionamento | Adicionado `val tempo_aproximado: Boolean = false` em `Acionamento.kt` e garantido envio no `HistoricoService`. | `38059bc` | ✅ Corrigido |
| **DIV-008** | 🟡 Média | `util.py:35-41`, `routers/notificacoes.py:18-29` | `model/Notificacao.kt`, `NotificacoesActivity.kt`, `NotificacoesAdapter.kt` | Faltava o campo `criada_em` (string ISO 8601); app esperava timestamp numérico `data: Long` | Adicionado `var criada_em: String = ""` com getter tolerante `timestampMillis` que converte ISO 8601 UTC para milissegundos para ordenação e formatação no app. | `a69b64d` | ✅ Corrigido |
| **DIV-009** | 🟡 Média | `routers/dispositivos.py:27-32`, `schemas.py:106` | `model/DadosDispositivo.kt` | Faltavam campos de telemetria e rede: `ssid`, `sinal_wifi`, `ip`, `firmware`, `bateria_pct`, `volume`, `wifi_versao` etc. | Adicionados campos completos de telemetria e Wi-Fi v7 no `DadosDispositivo.kt` com propriedade `bateriaEfetiva`. | `be815bc` | ✅ Corrigido |
| **DIV-010** | 🟡 Média | `schemas.py:9-13`, `routers/perfil.py:43-49` | `ConfiguracoesActivity.kt` | `modo_escuro` e `tamanho_fonte` não eram sincronizados com o Firestore no documento `/usuarios/{uid}` | Implementada sincronização com `SetOptions.merge()` em `/usuarios/{uid}` ao alterar tema escuro e escala de fonte (mapeada para inteiro 8..14). | `6fcd058` | ✅ Corrigido |
| **DIV-011** | 🟢 Baixa | `routers/perfil.py:18-35` | `model/Usuario.kt` | `Usuario.kt` continha campo `senha` e não mapeava preferências nem `dispositivosVinculados` | Expandido modelo `Usuario` com campos da Web, adicionado `@get:Exclude` no atributo `senha` para proteção de credenciais. | `ce93854` | ✅ Corrigido |
| **DIV-012** | 🟢 Baixa | `metricas.py:90-110` | `DesempehoService.kt`, `DesempenhoActivity.kt` | Injeção de valores mock `listOf(3f, 5f, 2f...)` quando não há acionamentos | Eliminados todos os dados fictícios. Gráficos vazios exibem `0f` por dia e métricas tratam períodos sem acionamento como "Nenhum". | `23f6180` | ✅ Corrigido |

---

## 4. Classificação dos Componentes Kotlin

- **Categoria (A) - Tem Equivalente na API Web (Alinhados nesta tarefa):**
  - Modelos de dados: `Categoria`, `CategoriaItem`, `Acionamento`, `Notificacao`, `DadosDispositivo`, `Usuario`.
  - Serviços de persistência: `CategoriaService`, `CategoriaRepository`, `HistoricoService`, `DispositivoService`, `Esp32ImageProcessor`, `DesempehoService`.
  - Configurações de perfil e acessibilidade: `ConfiguracoesActivity`, `PerfilActivity`.
- **Categoria (B) - Exclusivos do Mobile (Preservados integralmente):**
  - Captura e recorte de foto nativa via Câmera/Galeria (`ActivityResultContracts`).
  - Gravação de áudio nativa via `MediaRecorder` com visualizador de onda (`AudioWaveformView`, `ModalGravadorAudio`).
  - Síntese de voz nativa via Android `TextToSpeech` (`TTS`).
  - Feedback auditivo imediato (`SoundManager.dispararAudioCue`).
  - Cache local resiliente em SharedPreferences e `filesDir` (`MidiaLocalHelper`).
  - Permissões dinâmicas do Android (`RECORD_AUDIO`, `CAMERA`, `POST_NOTIFICATIONS`).
- **Categoria (C) - Itens existentes só no Kotlin para revisão do usuário:**
  - `DesenhoBotao`: Enum com banco visual local de pictogramas e links de Drive (recurso de biblioteca visual do app).
  - Coleções raiz legadas `/categoria` e `/categoria_botao`: mantidas apenas como fallback retrocompatível de leitura/escrita caso dispositivos legados ainda consultem a raiz.

---

## 5. Histórico de Commits na Branch `alinhamento-api-web`

1. `a7b7585`: `fix(DIV-005, DIV-006): alinhar schema de Categoria e CategoriaItem com API web`
2. `c9cb09f`: `fix(DIV-001, DIV-002, DIV-003): colecao canonica de categorias, limite 3 e bump_dispositivos`
3. `c92f65f`: `fix(DIV-004): alinhar TAMANHO_BLOCO de midias para 196608 bytes`
4. `38059bc`: `fix(DIV-007): adicionar tempo_aproximado em Acionamento`
5. `a69b64d`: `fix(DIV-008): suporte a criada_em em formato ISO 8601 em Notificacao`
6. `be815bc`: `fix(DIV-009): expandir DadosDispositivo com telemetria e rede v7`
7. `6fcd058`: `fix(DIV-010): persistir modo_escuro e tamanho_fonte no Firestore`
8. `ce93854`: `fix(DIV-011): alinhar schema de Usuario com perfil da API web`
9. `23f6180`: `fix(DIV-012): eliminar dados mockados em DesempehoService e DesempenhoActivity`
10. `aaae437`: `fix(DIV-005, DIV-006): construtor de compatibilidade posicional em CategoriaItem`

---

## 6. Verificação de Integridade e Build

- **Kotlin Compiler:** `compileDebugKotlin` executado com sucesso (`BUILD SUCCESSFUL`).
- **Android APK Build:** `assembleDebug` executado com sucesso (`BUILD SUCCESSFUL` em 4s).
- **Branch atual:** `alinhamento-api-web` (sem merge ou push para `main`, conforme especificado).
