package br.com.financasconscientes.csv

import br.com.financasconscientes.model.ImportedTransaction
import br.com.financasconscientes.model.TransactionDirection
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.Charset
import java.security.MessageDigest
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

data class CsvImportResult(
    val transactions: List<ImportedTransaction>,
    val headers: List<String>,
    val delimiter: Char,
)

class CsvImportException(message: String) : IllegalArgumentException(message)

object PicPayCsvParser {
    private const val MAX_ROWS = 20_000
    private const val MAX_FIELD_LENGTH = 500

    fun parse(bytes: ByteArray, importedAtEpochMillis: Long = System.currentTimeMillis()): CsvImportResult {
        val text = decode(bytes).replace("\r\n", "\n").replace('\r', '\n').trim()
        if (text.isBlank()) throw CsvImportException("O CSV está vazio.")

        val delimiter = detectDelimiter(text.lineSequence().first())
        val rows = parseRows(text, delimiter)
            .filter { row -> row.any { it.isNotBlank() } }
        if (rows.size < 2) throw CsvImportException("O CSV não contém movimentações.")
        if (rows.size - 1 > MAX_ROWS) throw CsvImportException("O CSV excede o limite de $MAX_ROWS movimentações.")

        val headers = rows.first().map { it.cleaned() }
        val normalizedHeaders = headers.map(::normalize)
        val dateIndex = findHeader(normalizedHeaders, DATE_HEADERS)
            ?: throw CsvImportException("Não foi possível identificar a coluna de data.")
        val timeIndex = findHeader(normalizedHeaders, TIME_HEADERS)
        val amountIndex = findHeader(normalizedHeaders, AMOUNT_HEADERS)
            ?: throw CsvImportException("Não foi possível identificar a coluna de valor.")
        val typeIndex = findHeader(normalizedHeaders, TYPE_HEADERS)
        val descriptionIndex = findHeader(normalizedHeaders, DESCRIPTION_HEADERS, excluding = setOfNotNull(typeIndex))
        val counterpartyIndex = findHeader(normalizedHeaders, COUNTERPARTY_HEADERS)
        val statusIndex = findHeader(normalizedHeaders, STATUS_HEADERS)
        val paymentMethodIndex = findHeader(normalizedHeaders, PAYMENT_METHOD_HEADERS)
        val occurrenceCounts = mutableMapOf<String, Int>()

        val transactions = rows.drop(1).mapIndexedNotNull { rowIndex, row ->
            if (row.size > headers.size + 2) {
                throw CsvImportException("A linha ${rowIndex + 2} tem colunas incompatíveis com o cabeçalho.")
            }
            val padded = List(headers.size) { index -> row.getOrNull(index).orEmpty().cleaned() }
            val dateOnly = padded[dateIndex]
            val timeText = timeIndex?.let(padded::get).orEmpty()
            val dateText = listOf(dateOnly, timeText).filter(String::isNotBlank).joinToString(" ")
            val amountText = padded[amountIndex]
            if (dateText.isBlank() && amountText.isBlank()) return@mapIndexedNotNull null
            val amountCents = parseAmountCents(amountText)
                ?: throw CsvImportException("Valor inválido na linha ${rowIndex + 2}.")
            val type = typeIndex?.let(padded::get).orEmpty()
            val description = descriptionIndex?.let(padded::get).orEmpty()
            val counterparty = counterpartyIndex?.let(padded::get).orEmpty()
            val status = statusIndex?.let(padded::get).orEmpty()
            val paymentMethod = paymentMethodIndex?.let(padded::get).orEmpty()
            val context = listOf(type, description, counterparty).joinToString(" ")
            val baseFingerprint = sha256(padded.joinToString("\u001F") { it.trim().lowercase(Locale.ROOT) })
            val occurrence = occurrenceCounts.getOrDefault(baseFingerprint, 0)
            occurrenceCounts[baseFingerprint] = occurrence + 1
            ImportedTransaction(
                fingerprint = sha256("$baseFingerprint#$occurrence"),
                importedAtEpochMillis = importedAtEpochMillis,
                occurredAtEpochMillis = parseDateTime(dateText),
                dateText = dateText,
                type = type,
                description = description,
                counterparty = counterparty,
                status = status,
                paymentMethod = paymentMethod,
                amountCents = amountCents,
                direction = direction(amountCents, context),
            )
        }.distinctBy { it.fingerprint }

        if (transactions.isEmpty()) throw CsvImportException("Nenhuma movimentação válida foi encontrada.")
        return CsvImportResult(transactions, headers, delimiter)
    }

    private fun decode(bytes: ByteArray): String {
        val withoutBom = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            bytes.copyOfRange(3, bytes.size)
        } else {
            bytes
        }
        val utf8 = withoutBom.toString(Charsets.UTF_8)
        return if ('\uFFFD' in utf8) withoutBom.toString(Charset.forName("windows-1252")) else utf8
    }

    private fun detectDelimiter(headerLine: String): Char {
        val candidates = listOf(';', ',', '\t')
        return candidates.maxByOrNull { delimiter -> parseRows(headerLine, delimiter).firstOrNull()?.size ?: 0 }
            ?: ';'
    }

    internal fun parseRows(text: String, delimiter: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var index = 0
        while (index < text.length) {
            val char = text[index]
            when {
                char == '"' && quoted && index + 1 < text.length && text[index + 1] == '"' -> {
                    field.append('"')
                    index++
                }
                char == '"' -> quoted = !quoted
                char == delimiter && !quoted -> {
                    row += field.toString()
                    field.setLength(0)
                }
                char == '\n' && !quoted -> {
                    row += field.toString()
                    rows += row.toList()
                    row.clear()
                    field.setLength(0)
                }
                else -> field.append(char)
            }
            index++
        }
        if (quoted) throw CsvImportException("O CSV contém aspas não encerradas.")
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row += field.toString()
            rows += row.toList()
        }
        return rows
    }

    internal fun parseAmountCents(raw: String): Long? {
        var value = raw.trim()
            .replace('−', '-')
            .replace('–', '-')
            .replace("R$", "", ignoreCase = true)
            .replace("\u00A0", "")
            .replace(" ", "")
        if (value.isBlank()) return null
        val parenthesized = value.startsWith('(') && value.endsWith(')')
        if (parenthesized) value = value.substring(1, value.length - 1)

        value = when {
            value.contains(',') && value.contains('.') -> {
                if (value.lastIndexOf(',') > value.lastIndexOf('.')) value.replace(".", "").replace(',', '.')
                else value.replace(",", "")
            }
            value.contains(',') -> value.replace('.', ' ').replace(" ", "").replace(',', '.')
            else -> value
        }
        val number = value.toBigDecimalOrNull() ?: return null
        val signed = if (parenthesized) number.negate() else number
        return signed.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).longValueExact()
    }

    private fun direction(amountCents: Long, context: String): TransactionDirection {
        if (amountCents < 0) return TransactionDirection.OUTFLOW
        val normalized = normalize(context)
        if (OUTFLOW_WORDS.any(normalized::contains)) return TransactionDirection.OUTFLOW
        if (INFLOW_WORDS.any(normalized::contains)) return TransactionDirection.INFLOW
        return if (amountCents > 0) TransactionDirection.INFLOW else TransactionDirection.UNKNOWN
    }

    private fun parseDateTime(raw: String): Long? {
        val value = raw.trim()
        DATE_TIME_FORMATTERS.forEach { formatter ->
            try {
                return LocalDateTime.parse(value, formatter).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
                // Try the next documented/common Brazilian representation.
            }
        }
        DATE_FORMATTERS.forEach { formatter ->
            try {
                return LocalDate.parse(value, formatter).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
                // Try the next representation.
            }
        }
        return null
    }

    private fun findHeader(headers: List<String>, aliases: Set<String>, excluding: Set<Int> = emptySet()): Int? {
        headers.forEachIndexed { index, header ->
            if (index !in excluding && header in aliases) return index
        }
        headers.forEachIndexed { index, header ->
            if (index !in excluding && aliases.any { alias -> header.contains(alias) }) return index
        }
        return null
    }

    private fun String.cleaned(): String = trim().take(MAX_FIELD_LENGTH)

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")
        .lowercase(Locale.ROOT)
        .replace("[^a-z0-9]+".toRegex(), " ")
        .trim()

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    private val DATE_HEADERS = setOf("data", "data hora", "data da transacao", "data da movimentacao", "criado em")
    private val TIME_HEADERS = setOf("hora", "horario", "hora da transacao", "hora da movimentacao")
    private val AMOUNT_HEADERS = setOf("valor", "valor r", "valor da transacao", "valor da movimentacao", "amount")
    private val TYPE_HEADERS = setOf("tipo", "tipo de movimentacao", "movimentacao", "tipo da transacao", "transacao")
    private val DESCRIPTION_HEADERS = setOf("descricao", "detalhes", "historico", "nome", "titulo")
    private val COUNTERPARTY_HEADERS = setOf("origem destino", "destino", "origem", "recebedor", "pagador", "estabelecimento", "contraparte")
    private val STATUS_HEADERS = setOf("status", "situacao", "estado")
    private val PAYMENT_METHOD_HEADERS = setOf("forma de pagamento", "meio de pagamento", "payment method")
    private val OUTFLOW_WORDS = setOf("pix enviado", "pagamento", "compra", "saque", "taxa", "debito", "transferencia enviada")
    private val INFLOW_WORDS = setOf("pix recebido", "recebido", "cashback", "adicao", "deposito", "credito", "transferencia recebida")
    private val DATE_TIME_FORMATTERS = listOf(
        DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm:ss"),
        DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm"),
        DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm"),
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
    )
    private val DATE_FORMATTERS = listOf(
        DateTimeFormatter.ofPattern("dd/MM/uuuu"),
        DateTimeFormatter.ISO_LOCAL_DATE,
    )
}
