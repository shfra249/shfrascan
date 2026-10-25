package app.openscan.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun OcrLanguageDialog(
    languages: List<String>,
    selected: String,
    onRun: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var choice by remember(selected, languages) {
        mutableStateOf(if (selected in languages) selected else languages.firstOrNull() ?: "eng")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Text recognition") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Choose the language of the document. More languages can be imported in Settings.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                if (languages.isEmpty()) {
                    Text("No OCR language packs installed yet.")
                }
                languages.forEach { code ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { choice = code }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = choice == code,
                            onClick = { choice = code }
                        )
                        Text(languageLabel(code))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onRun(choice) },
                enabled = languages.isNotEmpty()
            ) { Text("Run OCR") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

fun languageLabel(code: String): String =
    code.split("+").joinToString(" + ") { part ->
        when (part) {
            "eng" -> "English"
            "deu" -> "German"
            "fra" -> "French"
            "spa" -> "Spanish"
            "ita" -> "Italian"
            "por" -> "Portuguese"
            "rus" -> "Russian"
            "chi_sim" -> "Chinese (Simplified)"
            "chi_tra" -> "Chinese (Traditional)"
            "ara" -> "Arabic"
            "hin" -> "Hindi"
            "jpn" -> "Japanese"
            "kor" -> "Korean"
            "nld" -> "Dutch"
            "pol" -> "Polish"
            "tur" -> "Turkish"
            "ukr" -> "Ukrainian"
            "vie" -> "Vietnamese"
            else -> part
        }
    }
