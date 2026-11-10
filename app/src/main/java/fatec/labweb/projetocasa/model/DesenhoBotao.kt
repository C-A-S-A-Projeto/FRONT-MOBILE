package fatec.labweb.projetocasa.model

import androidx.annotation.DrawableRes
import fatec.labweb.projetocasa.R

/**
 * Mapeamento oficial dos desenhos dos botões do aplicativo C.A.S.A.
 * Hospedados na pasta pública do Google Drive:
 * https://drive.google.com/drive/folders/1M0YG4iToPI-FTGQA4X0auz8QHTrNIO5a
 */
enum class DesenhoBotao(
    val id: String,
    val titulo: String,
    @DrawableRes val resId: Int,
    val driveUrl: String,
    val categoriaPadrao: String,
    val aliases: List<String> = emptyList()
) {
    // Subpasta: categoria-casa
    BANHO(
        "cat_btn_casa_banheiro",
        "Banheiro / Banho",
        R.drawable.cat_btn_casa_banheiro,
        "https://lh3.googleusercontent.com/d/12oZLF3IO_yLa9U8G48x-ToiplHxozouu",
        "casa",
        listOf("banheiro", "banho", "lavabo", "ducha")
    ),
    COZINHA(
        "cat_btn_casa_cozinha",
        "Cozinha / Comer",
        R.drawable.cat_btn_casa_cozinha,
        "https://lh3.googleusercontent.com/d/1-CbmVLzqff558SrhP2e5R0MA3PCH2Tl1",
        "casa",
        listOf("cozinha", "comer", "comida", "alimentacao", "alimento", "refeicao", "lanche")
    ),
    LAVANDERIA(
        "cat_btn_casa_lavanderia",
        "Lavanderia / Lavar",
        R.drawable.cat_btn_casa_lavanderia,
        "https://lh3.googleusercontent.com/d/1j5tSONFtzrIe516zYLj0IKeENobhuJaz",
        "casa",
        listOf("lavanderia", "lavar", "roupa", "varal", "limpeza")
    ),
    QUARTO(
        "cat_btn_casa_quarto",
        "Quarto / Dormir",
        R.drawable.cat_btn_casa_quarto,
        "https://lh3.googleusercontent.com/d/17iZ-X9-LTnGaU1L96XCQAqp-jFxVVZJm",
        "casa",
        listOf("quarto", "dormir", "cama", "descanso", "soneca")
    ),
    SALA(
        "cat_btn_casa_sala",
        "Sala / Assistir",
        R.drawable.cat_btn_casa_sala,
        "https://lh3.googleusercontent.com/d/1sc8MesTedAMmBIPdn6gH_3SzdcIwm-Gh",
        "casa",
        listOf("sala", "televisao", "tv", "sofa", "assistir", "estar")
    ),

    // Subpasta: categoria-desejos
    FESTA(
        "cat_btn_desejo_festa",
        "Festa / Comemorar",
        R.drawable.cat_btn_desejo_festa,
        "https://lh3.googleusercontent.com/d/1_jgw-Q44l42Y6DDdehkTWhHQnBroSH3s",
        "desejos",
        listOf("festa", "comemorar", "aniversario", "parabens", "brincar")
    ),
    LER(
        "cat_btn_desejo_ler",
        "Ler / Livro",
        R.drawable.cat_btn_desejo_ler,
        "https://lh3.googleusercontent.com/d/1LzIu3uF4pZwXPm5c4bLFWV0XH03g4Hvc",
        "desejos",
        listOf("ler", "livro", "estudo", "historia", "leitura", "escola")
    ),
    MUSICA(
        "cat_btn_desejo_musica",
        "Música / Ouvir",
        R.drawable.cat_btn_desejo_musica,
        "https://lh3.googleusercontent.com/d/1G1L3uqnaOigjvUK3TF3OMD6aRsE6EJ4i",
        "desejos",
        listOf("musica", "som", "cantar", "ouvir", "cancoes", "dancar")
    ),
    SILENCIO(
        "cat_btn_desejo_silencio",
        "Silêncio / Calma",
        R.drawable.cat_btn_desejo_silencio,
        "https://lh3.googleusercontent.com/d/1UXz1AsZMtWNktV4d9p-a_rjil30AD1Cb",
        "desejos",
        listOf("silencio", "calma", "quieto", "paz", "descansar", "tranquilo")
    ),
    AJUDA(
        "cat_btn_desejo_ajuda",
        "Ajuda / Socorro",
        R.drawable.cat_btn_desejo_ajuda,
        "https://lh3.googleusercontent.com/d/1GRyMIUhPeaYtjA_ztwe4D-BMWQml-J2o",
        "desejos",
        listOf("ajuda", "socorro", "urgencia", "dor", "preciso", "auxilio", "emergencia")
    ),

    // Subpasta: categoria-emocoes
    FELIZ(
        "cat_btn_emocao_feliz",
        "Feliz / Alegre",
        R.drawable.cat_btn_emocao_feliz,
        "https://lh3.googleusercontent.com/d/1pA5Rs5cvYEDCdMrmxeSJHlhcRGqAcCPk",
        "emocoes",
        listOf("feliz", "alegre", "contente", "bem", "sorriso", "otimo")
    ),
    MEDO(
        "cat_btn_emocao_medo",
        "Medo / Assustado",
        R.drawable.cat_btn_emocao_medo,
        "https://lh3.googleusercontent.com/d/1WLdqFbO9s1Hnu4fh7g-zh_aJ95RyJgvM",
        "emocoes",
        listOf("medo", "assustado", "receio", "susto")
    ),
    NOJO(
        "cat_btn_emocao_nojo",
        "Nojo / Eca",
        R.drawable.cat_btn_emocao_nojo,
        "https://lh3.googleusercontent.com/d/1-hCJK9c-VyyvFw28oleDRbxws-utq0lu",
        "emocoes",
        listOf("nojo", "eca", "desagradavel", "ruim", "enjoado")
    ),
    RAIVA(
        "cat_btn_emocao_raiva",
        "Raiva / Bravo",
        R.drawable.cat_btn_emocao_raiva,
        "https://lh3.googleusercontent.com/d/1E82x9KBcUnwWeUZgFzwjhswrfApMoept",
        "emocoes",
        listOf("raiva", "bravo", "irritado", "nervoso", "odio")
    ),
    TRISTE(
        "cat_btn_emocao_triste",
        "Triste / Choro",
        R.drawable.cat_btn_emocao_triste,
        "https://lh3.googleusercontent.com/d/1V67P7Pqp7oNYUu8_HacTnKvUxNvhmPlN",
        "emocoes",
        listOf("triste", "choro", "tristeza", "chateado", "desanimado")
    ),

    // Subpasta: Personagens
    PERSONAGEM_1(
        "desenho_personagem_1",
        "Personagem 1",
        R.drawable.desenho_personagem_1,
        "https://lh3.googleusercontent.com/d/1DDi8tihHawaSs0d7dfItLIyyZMZ7i8UA",
        "personagens",
        listOf("personagem1", "boneco1", "menino")
    ),
    PERSONAGEM_2(
        "desenho_personagem_2",
        "Personagem 2",
        R.drawable.desenho_personagem_2,
        "https://lh3.googleusercontent.com/d/1mT7leJxVb1q_z-5LfP0ahVFieoKztVQv",
        "personagens",
        listOf("personagem2", "boneco2")
    ),
    PERSONAGEM_3(
        "desenho_personagem_3",
        "Personagem 3",
        R.drawable.desenho_personagem_3,
        "https://lh3.googleusercontent.com/d/1ACJMc7-Mw5B86TROPN1QC1Jp1gxxjmDB",
        "personagens",
        listOf("personagem3", "boneco3")
    ),
    PERSONAGEM_4(
        "desenho_personagem_4",
        "Personagem 4",
        R.drawable.desenho_personagem_4,
        "https://lh3.googleusercontent.com/d/1dxDnSieCwkqrBw9ZgTKoyi1xkfWFlG1Q",
        "personagens",
        listOf("personagem4", "boneco4")
    ),
    PERSONAGEM_5(
        "desenho_personagem_5",
        "Personagem 5",
        R.drawable.desenho_personagem_5,
        "https://lh3.googleusercontent.com/d/1yfa8bhwGUUUx5VKYSdDH9UIKCzJZqg3f",
        "personagens",
        listOf("personagem5", "boneco5")
    ),
    PERSONAGEM_6(
        "desenho_personagem_6",
        "Personagem 6",
        R.drawable.desenho_personagem_6,
        "https://lh3.googleusercontent.com/d/1YeharqTANle_wRFabPrxm5KsYmHUbUIE",
        "personagens",
        listOf("personagem6", "boneco6")
    ),
    PERSONAGEM_CAPA(
        "desenho_personagem_capa",
        "Personagem Capa",
        R.drawable.desenho_personagem_capa,
        "https://lh3.googleusercontent.com/d/15tSvWw2cGKw3HXspmywwJ5K_zdbaBPyp",
        "personagens",
        listOf("personagem_capa", "capa", "mascote")
    );

    companion object {
        fun buscarPorTermo(termo: String?): DesenhoBotao? {
            if (termo.isNullOrBlank()) return null
            val normalizado = termo.trim().lowercase()
                .replace("ç", "c")
                .replace("ã", "a")
                .replace("á", "a")
                .replace("é", "e")
                .replace("í", "i")
                .replace("ó", "o")
                .replace("ú", "u")

            return values().firstOrNull { desenho ->
                desenho.id.equals(normalizado, ignoreCase = true) ||
                desenho.name.equals(normalizado, ignoreCase = true) ||
                normalizado.contains(desenho.id.lowercase()) ||
                desenho.titulo.lowercase().contains(normalizado) ||
                desenho.aliases.any { alias -> normalizado.contains(alias) || alias.contains(normalizado) }
            }
        }

        @DrawableRes
        fun obterDrawable(vararg termos: String?): Int? {
            for (termo in termos) {
                val desenho = buscarPorTermo(termo)
                if (desenho != null) return desenho.resId
            }
            return null
        }
    }
}
