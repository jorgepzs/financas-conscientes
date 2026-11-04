package br.com.financasconscientes.model

data class ExpenseCategoryColor(val name: String, val hex: String)

object ExpenseCategoryPalette {
    val choices = listOf(
        ExpenseCategoryColor("Coral", "#FF8A65"),
        ExpenseCategoryColor("Laranja", "#FFA726"),
        ExpenseCategoryColor("Âmbar", "#FFCA28"),
        ExpenseCategoryColor("Lima", "#9CCC65"),
        ExpenseCategoryColor("Verde", "#66BB6A"),
        ExpenseCategoryColor("Turquesa", "#26A69A"),
        ExpenseCategoryColor("Ciano", "#26C6DA"),
        ExpenseCategoryColor("Azul", "#42A5F5"),
        ExpenseCategoryColor("Índigo", "#5C6BC0"),
        ExpenseCategoryColor("Violeta", "#AB47BC"),
        ExpenseCategoryColor("Rosa", "#EC407A"),
        ExpenseCategoryColor("Cinza", "#90A4AE"),
    )

    private val defaults = mapOf(
        "Outro" to "#90A4AE",
        "Alimentação" to "#FF8A65",
        "Moradia" to "#AB47BC",
        "Transporte" to "#42A5F5",
        "Saúde" to "#66BB6A",
        "Educação" to "#5C6BC0",
        "Lazer" to "#EC407A",
        "Compras" to "#FFA726",
        "Assinaturas" to "#26C6DA",
        "Dívidas" to "#FFCA28",
        "Transferência" to "#26A69A",
        "Sem categoria" to "#90A4AE",
    )

    fun defaultFor(category: String): String = defaults[category]
        ?: choices[Math.floorMod(category.hashCode(), choices.size)].hex

    fun normalize(hex: String?): String? = hex
        ?.trim()
        ?.takeIf { it.matches(Regex("#[0-9A-Fa-f]{6}")) }
        ?.uppercase()

    fun colorFor(category: String, overrides: Map<String, String>): String =
        normalize(overrides[category]) ?: defaultFor(category)
}
