package br.com.financasconscientes.navigation

enum class PrimaryDestination(val savedKey: String, val label: String) {
    HOME("home", "Início"),
    RESULTS("results", "Resultados"),
    RECORDS("records", "Registros");

    companion object {
        fun fromSavedKey(key: String?): PrimaryDestination =
            values().firstOrNull { it.savedKey == key } ?: HOME
    }
}
