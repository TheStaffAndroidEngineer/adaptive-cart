package staff.android.adaptive

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.text.NumberFormat

@Composable
fun productName(id: String): String = stringResource(
    if (id == "camera") R.string.camera else R.string.tea,
)

@Composable
fun CartScreen(
    state: CartState,
    onSelect: (String?) -> Unit,
    onEdit: (String) -> Unit,
    onChange: (String, String) -> Unit,
    onCancel: (String) -> Unit,
    onReload: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onRetry: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedId = state.selectedId
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var lastSelected by rememberSaveable { mutableStateOf<String?>(null) }
    val returnFocus = remember { FocusRequester() }
    val leaveDetail = {
        focus.clearFocus(); keyboard?.hide(); onSelect(null)
    }
    if (state.lines.isEmpty()) {
        Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(if (state.issue == CartIssue.Load) R.string.load_error else R.string.loading))
            if (state.issue == CartIssue.Load) Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
        return
    }
    BoxWithConstraints(modifier.padding(16.dp).testTag("cart-host")) {
        val twoPanes = showsTwoPanes(maxWidth.value)
        BackHandler(enabled = !twoPanes && selectedId != null) { leaveDetail() }
        LaunchedEffect(selectedId) {
            selectedId?.let { lastSelected = it }
            if (selectedId == null && lastSelected != null) returnFocus.requestFocus()
        }
        Row(Modifier.fillMaxSize().testTag(if (twoPanes) "two-panes" else "one-pane")) {
            if (twoPanes || selectedId == null) {
                CartList(
                    state, onSelect, onReset, returnFocus, lastSelected,
                    if (twoPanes) Modifier.width(320.dp) else Modifier.fillMaxWidth(),
                )
                if (twoPanes) Spacer(Modifier.width(24.dp))
            }
            // This editor has one call site; adding the list does not replace it.
            if (selectedId != null) {
                key(selectedId) {
                    QuantityPane(
                        state, selectedId, !twoPanes, leaveDetail,
                        onEdit, onChange, onCancel, onReload, onConfirm,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else if (twoPanes) {
                Text(stringResource(R.string.select_item), Modifier.weight(1f))
            }
        }

    }
}

@Composable
fun CartList(
    state: CartState,
    onSelect: (String?) -> Unit,
    onReset: () -> Unit,
    returnFocus: FocusRequester,
    lastSelected: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxHeight().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CommonGoodsHeader()
        Text(stringResource(R.string.cart_title), style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() })
        for (product in CartProducts) {
            val line = state.lines[product.id] ?: continue
            val target = if (lastSelected == product.id) Modifier.focusRequester(returnFocus) else Modifier
            Card(
                onClick = { onSelect(product.id) },
                modifier = Modifier.fillMaxWidth().then(target).testTag("item-${product.id}")
                    .semantics { selected = state.selectedId == product.id },
            ) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProductPhoto(product.id, Modifier.size(72.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(productName(product.id), style = MaterialTheme.typography.titleLarge)
                    if (state.selectedId == product.id) {
                        Text(stringResource(R.string.selected_item),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.clearAndSetSemantics {})
                    }
                    Text(stringResource(R.string.accepted_quantity, NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0]).format(line.quantity)))
                    }
                }
            }
        }
        LabControls {
            if (state.issue == CartIssue.Reset) {
                Text(stringResource(R.string.reset_error), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            OutlinedButton(onClick = onReset, enabled = !state.busy, modifier = Modifier.testTag("reset")) {
                Text(stringResource(R.string.reset))
            }
        }
    }
}

@Composable
fun QuantityPane(
    state: CartState,
    id: String,
    compact: Boolean,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onChange: (String, String) -> Unit,
    onCancel: (String) -> Unit,
    onReload: (String) -> Unit,
    onConfirm: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val line = state.lines[id] ?: return
    val draft = state.drafts[id]
    val name = productName(id)
    val entryFocus = remember { FocusRequester() }
    val editFocus = remember { FocusRequester() }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val accept = {
        if (state.canConfirm(id)) {
            focus.clearFocus(); keyboard?.hide(); onConfirm(id)
        }
    }
    LaunchedEffect(draft != null) {
        if (draft != null) entryFocus.requestFocus() else editFocus.requestFocus()
    }
    Column(
        modifier.fillMaxHeight().verticalScroll(rememberScrollState())
            .semantics { paneTitle = name }.testTag("editor-scroll"),
    ) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (compact) TextButton(onClick = onBack, modifier = Modifier.testTag("back-cart")) {
                Text(stringResource(R.string.back_cart))
            }
            CommonGoodsHeader()
            Text(name, style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.accepted_quantity, NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0]).format(line.quantity)),
                modifier = Modifier.testTag("accepted"))
            if (draft == null) {
                Button(onClick = { onEdit(id) }, enabled = !state.busy, modifier = Modifier.focusRequester(editFocus).testTag("edit")) {
                    Text(stringResource(R.string.edit_quantity))
                }
            } else {
                val invalid = state.candidate(id) == null
                val errorMessage = when {
                    state.isStale(id) -> stringResource(R.string.stale_draft)
                    invalid -> stringResource(R.string.invalid_quantity)
                    else -> null
                }
                QuantityField(
                    draft.raw, !state.busy, errorMessage,
                    { onChange(id, it) }, accept, entryFocus,
                )
                if (errorMessage != null) {
                    Text(errorMessage, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("validation"))
                }
                QuantityCommands(
                    state.canConfirm(id), state.busy, state.isStale(id),
                    accept, { onCancel(id) }, { onReload(id) },
                )
            }
            if (state.issue == CartIssue.Save && state.issueItemId == id) {
                Text(stringResource(R.string.save_error), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            QuantityHelp()
        }
    }
}


@Composable
fun QuantityField(
    raw: String,
    enabled: Boolean,
    errorMessage: String?,
    onRawChange: (String) -> Unit,
    onConfirm: () -> Unit,
    requester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = raw,
        onValueChange = onRawChange,
        label = { Text(stringResource(R.string.quantity_label)) },
        isError = errorMessage != null,
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number, imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onConfirm() }),
        modifier = modifier.fillMaxWidth().focusRequester(requester).testTag("draft")
            .semantics { if (errorMessage != null) error(errorMessage) },
    )
}

@Composable
fun QuantityCommands(
    canConfirm: Boolean,
    busy: Boolean,
    stale: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onReload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth().focusGroup(),
    ) {
        Button(onClick = onConfirm, enabled = canConfirm,
            modifier = Modifier.heightIn(min = 48.dp).testTag("confirm")) {
            Text(stringResource(if (busy) R.string.saving else R.string.confirm))
        }
        OutlinedButton(onClick = onCancel, enabled = !busy,
            modifier = Modifier.heightIn(min = 48.dp).testTag("cancel")) {
            Text(stringResource(R.string.cancel))
        }
        if (stale) OutlinedButton(onClick = onReload, enabled = !busy) {
            Text(stringResource(R.string.reload))
        }
    }
}

@Composable
fun QuantityHelp() {
    var showHelp by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { showHelp = !showHelp }, modifier = Modifier.testTag("help")) {
        Text(stringResource(if (showHelp) R.string.help_hide else R.string.help_show))
    }
    AnimatedVisibility(
        visible = showHelp,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Text(stringResource(R.string.help_text), modifier = Modifier.testTag("help-text"))
    }
}
