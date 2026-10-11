package staff.android.adaptive

data class Product(val id: String, val maximum: Int = 5)
val CartProducts = listOf(Product("camera"), Product("tea"))
data class CartLine(val quantity: Int = 2, val revision: Long = 0)
data class QuantityDraft(val raw: String, val baseRevision: Long)
enum class CartIssue { Load, Save, Conflict, Reset }
data class CartState(
    val lines: Map<String, CartLine> = emptyMap(),
    val drafts: Map<String, QuantityDraft> = emptyMap(),
    val selectedId: String? = null,
    val busy: Boolean = false,
    val issue: CartIssue? = null,
    val issueItemId: String? = null,
) {
    fun candidate(id: String): Int? = drafts[id]?.raw?.trim()
        ?.toIntOrNull()?.takeIf { it in 0..CartProducts.first { p -> p.id == id }.maximum }
    fun isStale(id: String): Boolean = drafts[id]?.let {
        lines[id]?.revision != it.baseRevision
    } ?: false
    fun canConfirm(id: String): Boolean = !busy && lines[id] != null &&
        candidate(id) != null && !isStale(id)
}

// A local pane budget for this two-item rectangular fixture, not a device class.
fun showsTwoPanes(availableWidthDp: Float): Boolean = availableWidthDp >= 840f

data class EpisodeUi(
    val id: String,
    val title: String,
    val playing: Boolean,
    val downloadPercent: Int,
) {
    init { require(id.isNotBlank()); require(downloadPercent in 0..100) }
}
