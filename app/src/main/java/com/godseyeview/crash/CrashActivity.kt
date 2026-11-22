package com.godseyeview.crash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.godseyeview.MainActivity
import com.godseyeview.R
import com.godseyeview.core.ui.GodsEyeTheme
import java.io.File

class CrashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val report = intent.getStringExtra(EXTRA_REPORT_FILE)
            ?.let { path -> runCatching { File(path).readText() }.getOrNull() }
            ?: getString(R.string.crash_no_report)
        setContent {
            GodsEyeTheme {
                CrashScreen(
                    report = report,
                    onCopy = {
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("crash", report))
                    },
                    onRestart = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    },
                    onClose = { finish() },
                )
            }
        }
    }

    companion object {
        const val EXTRA_REPORT_FILE = "report_file"
    }
}

@Composable
private fun CrashScreen(
    report: String,
    onCopy: () -> Unit,
    onRestart: () -> Unit,
    onClose: () -> Unit,
) {
    // Surface provides the theme's content color; a bare Column leaves
    // Text at the default (black) - issue #27.
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize(),
    ) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.crash_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            text = report,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onCopy) { Text(stringResource(R.string.crash_copy)) }
            Button(onClick = onRestart) { Text(stringResource(R.string.crash_restart)) }
            TextButton(onClick = onClose) { Text(stringResource(R.string.crash_close)) }
        }
    }
    }
}
