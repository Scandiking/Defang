package com.defang.launcher.service.nfc

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.defang.launcher.R
import com.defang.launcher.data.local.datastore.PreferencesDataStore
import com.defang.launcher.ui.theme.DefangTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The activity that actually reads an NFC tag — reader mode requires a resumed
 * Activity, which the gate's overlay (living in the AccessibilityService) is not.
 *
 * Two modes ([NfcUnlock.MODE_REGISTER] / [NfcUnlock.MODE_UNLOCK]):
 *  - REGISTER (from settings): capture the tapped tag's UID, store it, finish.
 *  - UNLOCK (from the gate): compare the tapped tag to the stored UID; on a match
 *    broadcast [NfcUnlock.ACTION_NFC_UNLOCKED] so the service opens the app.
 *    Backing out broadcasts [NfcUnlock.ACTION_NFC_GOBACK] — the same "Go back"
 *    the slide gate offers, i.e. the app is left unopened. There is deliberately
 *    no slide fallback here: NFC is available (the service only launches this
 *    when it is), so the only ways out are the right tag or backing out.
 */
@AndroidEntryPoint
class NfcUnlockActivity : ComponentActivity(), NfcAdapter.ReaderCallback {

    @Inject lateinit var prefs: PreferencesDataStore

    private var adapter: NfcAdapter? = null

    private val mode: String by lazy {
        intent.getStringExtra(NfcUnlock.EXTRA_MODE) ?: NfcUnlock.MODE_UNLOCK
    }
    private val appLabel: String by lazy {
        intent.getStringExtra(NfcUnlock.EXTRA_APP_LABEL).orEmpty()
    }

    private sealed interface Status {
        data object Waiting : Status
        data object WrongTag : Status
        data object NfcOff : Status
    }

    private var status by mutableStateOf<Status>(Status.Waiting)
    // Guards against double-handling a tag while the coroutine that finishes the
    // activity is still in flight.
    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = NfcAdapter.getDefaultAdapter(this)
        setContent {
            DefangTheme {
                BackHandler { goBack() }
                UnlockScreen(
                    isRegister = mode == NfcUnlock.MODE_REGISTER,
                    appLabel = appLabel,
                    nfcOff = status is Status.NfcOff,
                    wrongTag = status is Status.WrongTag,
                    onBack = { goBack() },
                    onOpenNfcSettings = {
                        startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val a = adapter
        if (a == null || !a.isEnabled) {
            status = Status.NfcOff
            return
        }
        status = Status.Waiting
        a.enableReaderMode(
            this,
            this,
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null,
        )
    }

    override fun onPause() {
        super.onPause()
        adapter?.disableReaderMode(this)
    }

    /** Reader-mode callback — runs on a binder thread, so hop to the main scope. */
    override fun onTagDiscovered(tag: Tag) {
        val uid = NfcUnlock.uidHex(tag.id)
        lifecycleScope.launch {
            if (handled) return@launch
            when (mode) {
                NfcUnlock.MODE_REGISTER -> {
                    handled = true
                    prefs.setNfcTagUid(uid)
                    setResult(RESULT_OK)
                    finish()
                }
                else -> {
                    val stored = prefs.nfcTagUid.first()
                    if (stored != null && stored.equals(uid, ignoreCase = true)) {
                        handled = true
                        sendBroadcast(
                            Intent(NfcUnlock.ACTION_NFC_UNLOCKED).setPackage(packageName)
                        )
                        finish()
                    } else {
                        // Wrong tag — keep reading so the user can try the right one.
                        status = Status.WrongTag
                    }
                }
            }
        }
    }

    private fun goBack() {
        if (handled) return
        handled = true
        if (mode == NfcUnlock.MODE_UNLOCK) {
            sendBroadcast(Intent(NfcUnlock.ACTION_NFC_GOBACK).setPackage(packageName))
        }
        finish()
    }
}

@Composable
private fun UnlockScreen(
    isRegister: Boolean,
    appLabel: String,
    nfcOff: Boolean,
    wrongTag: Boolean,
    onBack: () -> Unit,
    onOpenNfcSettings: () -> Unit,
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val wrong = wrongTag

            val title = when {
                nfcOff -> stringResource(R.string.nfc_off_title)
                isRegister -> stringResource(R.string.nfc_register_title)
                else -> stringResource(R.string.nfc_unlock_title, appLabel)
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Text(
                text = when {
                    nfcOff -> stringResource(R.string.nfc_off_body)
                    wrong -> stringResource(R.string.nfc_wrong_tag)
                    isRegister -> stringResource(R.string.nfc_register_body)
                    else -> stringResource(R.string.nfc_unlock_body)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = if (wrong)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )

            if (nfcOff) {
                TextButton(onClick = onOpenNfcSettings, modifier = Modifier.padding(top = 24.dp)) {
                    Text(stringResource(R.string.nfc_open_settings))
                }
            }

            TextButton(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
                Text(
                    text = if (isRegister)
                        stringResource(R.string.action_cancel)
                    else
                        stringResource(R.string.gate_go_back),
                )
            }
        }
    }
}
