package staff.android.adaptive

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

@Composable
fun CommonGoodsHeader() {
    Text("COMMON GOODS", style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary)
}

@Composable
fun ProductPhoto(id: String, modifier: Modifier = Modifier) {
    Image(painterResource(if (id == "camera") R.drawable.product_camera else R.drawable.product_tea),
        contentDescription = null, contentScale = ContentScale.Crop,
        modifier = modifier.clip(RoundedCornerShape(16.dp)))
}

@Composable
fun LabControls(content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val description = stringResource(if (expanded) R.string.lab_expanded else R.string.lab_collapsed)
    HorizontalDivider(Modifier.padding(top = 16.dp))
    TextButton(onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth().testTag("lab-controls").semantics {
            stateDescription = description
        }) {
        Text(stringResource(if (expanded) R.string.lab_hide else R.string.lab_show))
    }
    if (expanded) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}
