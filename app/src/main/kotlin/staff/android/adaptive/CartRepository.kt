package staff.android.adaptive

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.cartDataStore by preferencesDataStore("chapter8-cart")
class RevisionConflict : IllegalStateException()
interface CartRepository {
    val lines: Flow<Map<String, CartLine>>
    suspend fun confirm(id: String, baseRevision: Long, quantity: Int)
    suspend fun reset()
}
class DataStoreCartRepository(private val store: DataStore<Preferences>) : CartRepository {
    private fun read(p: Preferences, id: String) = CartLine(
        p[intPreferencesKey("$id-quantity")] ?: 2,
        p[longPreferencesKey("$id-revision")] ?: 0,
    )
    override val lines = store.data.map { p -> CartProducts.associate { it.id to read(p, it.id) } }
    override suspend fun confirm(id: String, baseRevision: Long, quantity: Int) {
        val product = CartProducts.first { it.id == id }
        require(quantity in 0..product.maximum)
        store.edit { p ->
            val current = read(p, id)
            if (current.revision != baseRevision) throw RevisionConflict()
            if (current.quantity != quantity) {
                p[intPreferencesKey("$id-quantity")] = quantity
                p[longPreferencesKey("$id-revision")] = current.revision + 1
            }
        }
    }
    override suspend fun reset() { store.edit { it.clear() } }
}
