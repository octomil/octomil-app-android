package ai.octomil.app.screens

import ai.octomil.app.models.PairedModel
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ModelDetailScreen(
    model: PairedModel,
    onTryModel: (modelName: String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Model name
        Text(
            text = model.name,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        // Version chip
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModelChip(text = "v${model.version}")
            ModelChip(text = model.runtime)
            if (model.modality != null) {
                ModelChip(text = model.modality)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Info card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ModelInfoRow("Name", model.name)
                ModelInfoRow("Version", model.version)
                ModelInfoRow("Size", model.sizeString)
                ModelInfoRow("Runtime", model.runtime)
                if (model.tokensPerSecond != null) {
                    ModelInfoRow("Tokens/sec", String.format("%.1f", model.tokensPerSecond))
                }
                if (model.modality != null) {
                    ModelInfoRow("Modality", model.modality)
                }
            }
        }

        // Try it out
        if (model.isChatModel) {
            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = { onTryModel(model.name) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(vertical = 14.dp),
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Interactive Demo")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ModelChip(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ModelInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
