package staff.android.adaptive.exercise

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import staff.android.adaptive.EpisodeUi
import staff.android.adaptive.R

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.key
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput

// Deliberately defective learner checkpoint. Reference code lives separately.
@Composable
fun EpisodeActionBar(
    ui: EpisodeUi,
    onPlayPause: (String) -> Unit,
    onDownload: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    key(ui.downloadPercent) {
        Row(modifier.fillMaxWidth().height(56.dp).clipToBounds()) {
            Text(ui.title, Modifier.width(200.dp))
            Text(
                stringResource(if (ui.playing) R.string.pause else R.string.play),
                Modifier.testTag("play-pause").pointerInput(Unit) {
                    detectTapGestures { onPlayPause(ui.id) }
                },
            )
            Button(onClick = { onDownload(ui.id) }, modifier = Modifier.testTag("download")) {
                Text(stringResource(R.string.download))
            }
            Button(onClick = { onRemove(ui.id) }, modifier = Modifier.testTag("remove")) {
                Text(stringResource(R.string.remove))
            }
        }
    }
}
