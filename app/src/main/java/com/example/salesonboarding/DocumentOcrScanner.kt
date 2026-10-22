package com.example.salesonboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.selection.SelectionContainer
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.IOException

@Composable
fun DocumentOcrScanner(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val recognizer = remember {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    var isScanning by remember { mutableStateOf(false) }
    var extractedText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(recognizer) {
        onDispose { recognizer.close() }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            isScanning = true
            extractedText = ""
            errorMessage = null
            try {
                val image = InputImage.fromFilePath(context, uri)
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        extractedText = result.text.trim()
                        if (extractedText.isBlank()) {
                            errorMessage = "No readable text found. Try a clear, well-lit document photo."
                        }
                    }
                    .addOnFailureListener {
                        errorMessage = "Could not read this image. Try a clearer document photo."
                    }
                    .addOnCompleteListener {
                        isScanning = false
                    }
            } catch (_: IOException) {
                isScanning = false
                errorMessage = "Could not open this image. Choose another document photo."
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedButton(
            onClick = { imagePicker.launch("image/*") },
            enabled = !isScanning,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isScanning) "Reading document…" else "Read text from a document photo")
        }
        Text(
            "Text is read on this device. The photo and scan result are not uploaded or saved automatically. Check every result with the customer.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        errorMessage?.let { message ->
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        if (extractedText.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "Text found · check before using",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    SelectionContainer {
                        Text(
                            extractedText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}
