package staff.android.adaptive

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp

@Composable
fun BrokenQuantityActions(
  raw: String,
  onRawChange: (String) -> Unit,
  onConfirm: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(modifier.fillMaxWidth().height(56.dp).clipToBounds()) {
    OutlinedTextField(
      value = raw,
      onValueChange = onRawChange,
      label = { Text("Quantity") },
      singleLine = true,
      modifier = Modifier.width(200.dp),
    )
    Spacer(Modifier.width(12.dp))
    Button(onClick = onConfirm, modifier = Modifier.width(104.dp)) {
      Text("Confirm")
    }
  }
}

@Composable
fun AdaptiveQuantityActions(
  raw: String,
  onRawChange: (String) -> Unit,
  onConfirm: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    OutlinedTextField(
      value = raw,
      onValueChange = onRawChange,
      label = { Text("Quantity") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )
    Button(
      onClick = onConfirm,
      modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
      Text("Confirm")
    }
  }
}

@Composable
fun QuantityLayoutProbe(broken: Boolean) {
  var accepted by rememberSaveable { mutableIntStateOf(2) }
  var raw by rememberSaveable { mutableStateOf("2") }
  val confirm = {
    raw.trim().toIntOrNull()?.takeIf { it in 0..5 }
      ?.let { accepted = it }
    Unit
  }
  Column(
    Modifier.width(280.dp).height(220.dp)
      .padding(horizontal = 16.dp)
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text("Accepted: $accepted")
    if (broken) {
      BrokenQuantityActions(raw, { raw = it }, confirm)
    } else {
      AdaptiveQuantityActions(raw, { raw = it }, confirm)
    }
  }
}
