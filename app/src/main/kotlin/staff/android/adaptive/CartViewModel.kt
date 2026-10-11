package staff.android.adaptive

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.IOException

object DraftCodec {
    fun encode(drafts: Map<String, QuantityDraft>) = Bundle().apply {
        putInt("schema", 1)
        drafts.forEach { (id, draft) ->
            putBundle(id, Bundle().apply {
                putString("raw", draft.raw)
                putLong("revision", draft.baseRevision)
            })
        }
    }
    fun decode(bundle: Bundle?): Map<String, QuantityDraft> {
        if (bundle?.getInt("schema") != 1) return emptyMap()
        return CartProducts.mapNotNull { product ->
            val item = bundle.getBundle(product.id) ?: return@mapNotNull null
            val raw = item.getString("raw") ?: return@mapNotNull null
            if (!item.containsKey("revision") || raw.length > 32) return@mapNotNull null
            val revision = item.getLong("revision")
            if (revision < 0) null else product.id to QuantityDraft(raw, revision)
        }.toMap()
    }
}
class CartViewModel(
    private val saved: SavedStateHandle,
    private val repository: CartRepository,
) : ViewModel() {
    private val lines = MutableStateFlow<Map<String, CartLine>>(emptyMap())
    private val drafts = saved.getStateFlow<Bundle?>("drafts", null)
    private val selection = saved.getStateFlow<String?>("selection", null)
    private val busy = MutableStateFlow(false)
    private val issue = MutableStateFlow<Pair<CartIssue, String?>?>(null)
    private var load: Job? = null
    val state = combine(lines, drafts, selection, busy, issue) { cart, bundle, id, working, failure ->
        CartState(cart, DraftCodec.decode(bundle), id?.takeIf { candidate ->
            CartProducts.any { it.id == candidate }
        }, working, failure?.first, failure?.second)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CartState())
    init { retry() }
    fun retry() {
        if (load?.isActive == true) return
        issue.value = null
        load = viewModelScope.launch {
            try { repository.lines.collect { lines.value = it } }
            catch (e: IOException) { lines.value = emptyMap(); issue.value = CartIssue.Load to null }
        }
    }
    fun select(id: String?) {
        require(id == null || CartProducts.any { it.id == id })
        saved["selection"] = id
    }
    private fun write(id: String, draft: QuantityDraft?) {
        val updated = DraftCodec.decode(drafts.value).toMutableMap()
        if (draft == null) updated.remove(id) else updated[id] = draft
        saved["drafts"] = DraftCodec.encode(updated)
    }
    private fun clearIssue(id: String) {
        if (issue.value?.second == id) issue.value = null
    }
    fun edit(id: String) {
        if (busy.value || DraftCodec.decode(drafts.value)[id] != null) return
        val line = lines.value[id] ?: return
        write(id, QuantityDraft(line.quantity.toString(), line.revision)); clearIssue(id)
    }
    fun change(id: String, raw: String) {
        if (busy.value || raw.length > 32) return
        val draft = DraftCodec.decode(drafts.value)[id] ?: return
        write(id, draft.copy(raw = raw)); clearIssue(id)
    }
    fun cancel(id: String) { if (!busy.value) { write(id, null); clearIssue(id) } }
    fun reload(id: String) {
        if (busy.value) return
        val line = lines.value[id] ?: return
        write(id, QuantityDraft(line.quantity.toString(), line.revision)); clearIssue(id)
    }
    fun confirm(id: String) {
        if (busy.value) return
        val current = CartState(lines.value, DraftCodec.decode(drafts.value), selection.value, busy.value, issue.value?.first, issue.value?.second)
        if (!current.canConfirm(id)) return
        val draft = DraftCodec.decode(drafts.value)[id] ?: return
        val quantity = draft.raw.trim().toIntOrNull()?.takeIf { it in 0..5 } ?: return
        busy.value = true
        viewModelScope.launch {
            try { repository.confirm(id, draft.baseRevision, quantity); write(id, null); clearIssue(id) }
            catch (e: CancellationException) { throw e }
            catch (e: RevisionConflict) { issue.value = CartIssue.Conflict to id }
            catch (e: IOException) { issue.value = CartIssue.Save to id }
            finally { busy.value = false }
        }
    }
    fun reset() {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try { repository.reset(); saved["drafts"] = null; saved["selection"] = null; issue.value = null }
            catch (e: IOException) { issue.value = CartIssue.Reset to null }
            finally { busy.value = false }
        }
    }
}
