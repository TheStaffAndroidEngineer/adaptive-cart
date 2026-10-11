package staff.android.adaptive

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class CartApplication : Application() {
    val repository: CartRepository by lazy { DataStoreCartRepository(cartDataStore) }
}
class MainActivity : ComponentActivity() {
    private val owner: CartViewModel by viewModels {
        viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as CartApplication
                CartViewModel(createSavedStateHandle(), app.repository)
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Box(Modifier.semantics { testTagsAsResourceId = true }) {
                CartTheme { CartApp(owner) }
            }
        }
    }
}
@Composable
fun CartApp(owner: CartViewModel) {
    val state by owner.state.collectAsStateWithLifecycle()
    Scaffold(
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
    ) { padding ->
        CartScreen(
            state = state,
            onSelect = owner::select,
            onEdit = owner::edit,
            onChange = owner::change,
            onCancel = owner::cancel,
            onReload = owner::reload,
            onConfirm = owner::confirm,
            onRetry = owner::retry,
            onReset = owner::reset,
            modifier = Modifier.fillMaxSize().padding(padding)
                .consumeWindowInsets(padding).imePadding(),
        )
    }
}
