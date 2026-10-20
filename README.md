# Projeto C.A.S.A. — App Mobile Android (`FRONT-MOBILE`)

Aplicativo Android (Kotlin) do **Projeto C.A.S.A.**, integrado ao Firebase (Authentication + Cloud Firestore) e à API compartilhada (`CASA-API`).

---

## 1. Estrutura de Pastas (Repositórios Lado a Lado)

Mantenha os repositórios clonados **lado a lado** dentro da mesma pasta raiz:

```text
CASA/
├── CASA-API/        # Backend compartilhado (FastAPI — porta 8000)
├── ProjetoCasa/     # Frontend Web (Next.js — porta 3000)
└── FRONT-MOBILE/    # Este repositório (App Android Kotlin)
```

---

## 2. Como Baixar e Iniciar a `CASA-API` (Backend)

1. Na pasta raiz (ao lado de `FRONT-MOBILE`), clone o repositório da API:
   ```bash
   git clone https://github.com/Projeto-C-A-S-A/CASA-API.git
   ```
2. Entre na pasta `CASA-API/backend`, crie o ambiente virtual e instale as dependências:
   ```powershell
   cd CASA-API/backend
   python -m venv venv
   venv\Scripts\activate
   python -m pip install -r requirements.txt
   ```
3. Adicione o arquivo `firebase-credentials.json` do projeto Firebase em `CASA-API/backend/` e crie o `.env`:
   ```powershell
   copy .env.example .env
   ```
4. Inicie o servidor FastAPI na porta `8000`:
   ```powershell
   python -m uvicorn main:app --reload --port 8000
   ```

---

## 3. Como Configurar e Iniciar o App Mobile (`FRONT-MOBILE`)

### Pré-requisitos
- **Android Studio** (Ladybug ou superior) com **JDK 11+** e Android SDK 37
- Arquivo `google-services.json` do mesmo projeto Firebase usado pela `CASA-API`

### Passo a passo
1. Baixe o arquivo `google-services.json` no Console do Firebase (*Configurações do Projeto → Seus aplicativos → App Android `fatec.labweb.projetocasa`*) e coloque-o em:
   ```text
   FRONT-MOBILE/app/google-services.json
   ```
2. Abra a pasta `FRONT-MOBILE` no **Android Studio** e aguarde o **Gradle Sync** finalizar.
3. Certifique-se de que a `CASA-API` está rodando na porta `8000`.
   - **No Emulador Android:** o app já aponta automaticamente para `http://10.0.2.2:8000` (que mapeia para o `localhost:8000` do seu computador).
   - **Em Celular Físico (via USB/Wi-Fi):** caso rode em um aparelho físico na mesma rede Wi-Fi, passe o IP da sua máquina no `gradle.properties` (ex.: `CASA_API_URL=http://192.168.0.10:8000`).
4. Clique em **Run (`Shift + F10`)** no Android Studio para instalar e abrir o app no emulador ou dispositivo.

---

## 4. Arquitetura de Comunicação com a `CASA-API` e o Banco

- **`CasaApiClient`**: envia automaticamente o `idToken` do `FirebaseAuth` no cabeçalho `Authorization: Bearer <token>` para todas as rotas privadas da `CASA-API`.
- **`UsuarioDao`**: realiza login/cadastro com verificação em 2 etapas (`/auth/cadastro` e `/auth/confirmar`) e sincroniza o documento `usuarios/{uid}` com `GET /me`.
- **`DispositivoDao`**: escuta em tempo real o documento `status_casa/atual` no Firestore e gerencia os aparelhos pareados em `/dispositivos`.
- **`CategoriaDao` e `HistoricoDao`**: consomem `/categorias`, `/midias`, `/historico`, `/desempenho` e `/notificacoes` na `CASA-API`.
