package br.com.financasconscientes.model

object ExpenseCategories {
    const val DEFAULT = "Outro"
    const val UNCATEGORIZED = "Sem categoria"

    val all = listOf(
        DEFAULT,
        "Alimentação",
        "Moradia",
        "Transporte",
        "Saúde",
        "Educação",
        "Lazer",
        "Compras",
        "Assinaturas",
        "Dívidas",
        "Transferência",
        UNCATEGORIZED,
    )
}

object ExpenseDecisions {
    const val DEFAULT = "Não informado"
    const val PLANNED = "Planejado"
    const val IMPULSE = "Impulso"

    val all = listOf(DEFAULT, PLANNED, IMPULSE)
}
