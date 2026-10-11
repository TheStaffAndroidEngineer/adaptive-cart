package staff.android.adaptive.solution

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

@Composable
fun EpisodeActionBar(
    ui: EpisodeUi,
    onPlayPause: (String) -> Unit,
    onDownload: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val progressLabel = stringResource(R.string.download_progress)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.episode_title, ui.title), style = MaterialTheme.typography.titleLarge)
        LinearProgressIndicator(
            progress = { ui.downloadPercent / 100f },
            modifier = Modifier.fillMaxWidth().testTag("episode-progress")
                .semantics { contentDescription = progressLabel },
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Button(onClick = { onPlayPause(ui.id) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("play-pause")) {
                Text(stringResource(if (ui.playing) R.string.pause else R.string.play))
            }
            OutlinedButton(onClick = { onDownload(ui.id) }, enabled = ui.downloadPercent == 0,
                modifier = Modifier.heightIn(min = 48.dp).testTag("download")) {
                Text(stringResource(R.string.download))
            }
            OutlinedButton(onClick = { onRemove(ui.id) }, enabled = ui.downloadPercent == 100,
                modifier = Modifier.heightIn(min = 48.dp).testTag("remove")) {
                Text(stringResource(R.string.remove))
            }
        }
    }
}
