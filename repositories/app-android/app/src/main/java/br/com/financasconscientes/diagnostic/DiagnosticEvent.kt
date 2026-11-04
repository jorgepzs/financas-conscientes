package br.com.financasconscientes.diagnostic

data class DiagnosticEvent(
    val id: String,
    val postedAtEpochMillis: Long,
    val packageName: String,
    val title: String,
    val text: String,
)
