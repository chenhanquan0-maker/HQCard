package com.hqcard.app

import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hqcard.detector.isDetectorEnabled
import com.hqcard.nfc.NfcAvailability
import com.hqcard.nfc.resolveAvailability
import com.hqcard.record.SwipeEvent
import com.hqcard.record.SwipeRecordStore

class MainActivity : ComponentActivity() {

    // ── 原「手机主动读卡」接线已按需求停用（手机改为被刷的卡，HCE 模式）──
    // 读卡实现完整保留在 modules/nfc（NfcCardReader），需要恢复时取消注释即可：
    // private val nfcReader by lazy { NfcCardReader.create(this) }

    private val repository by lazy { SwipeRecordStore.get(this) }
    private val viewModel: MainViewModel by viewModels {
        viewModelFactory {
            initializer {
                MainViewModel(
                    repository = repository,
                    availabilityProvider = {
                        resolveAvailability(NfcAdapter.getDefaultAdapter(this@MainActivity)?.isEnabled)
                    },
                    detectorStatusProvider = { isDetectorEnabled(this@MainActivity) },
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainScreen(
                    viewModel = viewModel,
                    onOpenAccessibilitySettings = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onForeground()
        // 原读卡模式：viewModel.onForeground(this) 内部会 reader.start(this) 开启 ReaderMode —— 已停用
    }

    // 原读卡模式：onPause 中 viewModel.onBackground(this) 停止 ReaderMode —— 已停用，
    // HCE 由系统在贴卡时自动唤醒 EmulatedCardService，无需前台注册。
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onOpenAccessibilitySettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("HQCard 刷卡记录") },
                actions = {
                    IconButton(onClick = { viewModel.clearRecords() }) {
                        Icon(Icons.Default.Delete, contentDescription = "清空全部")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            NfcStatusBanner(state.nfcAvailability)
            DetectorStatusCard(
                enabled = state.detectorEnabled,
                onOpenSettings = onOpenAccessibilitySettings,
            )
            AidCard(state.emulatedAid)
            if (state.records.isEmpty()) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "暂无记录\n双击电源唤出门卡刷卡，或贴近读卡器刷本机虚拟卡",
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.records, key = { it.id }) { record ->
                        RecordItem(
                            record = record,
                            onDelete = { viewModel.deleteRecord(record.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NfcStatusBanner(availability: NfcAvailability) {
    val (text, color) = when (availability) {
        NfcAvailability.ENABLED -> "NFC 就绪，手机作为卡片等待被刷" to MaterialTheme.colorScheme.primaryContainer
        NfcAvailability.DISABLED -> "NFC 已关闭，请在系统设置中开启后再刷卡" to MaterialTheme.colorScheme.errorContainer
        NfcAvailability.UNSUPPORTED -> "本设备不支持 NFC" to MaterialTheme.colorScheme.errorContainer
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = color,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun DetectorStatusCard(enabled: Boolean, onOpenSettings: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("小米钱包刷卡侦测", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.padding(2.dp))
                Text(
                    text = if (enabled) "已开启：双击电源刷卡将自动记录" else "未开启：需授予无障碍权限",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                )
            }
            if (!enabled) {
                TextButton(onClick = onOpenSettings) {
                    Text("去开启")
                }
            }
        }
    }
}

@Composable
private fun AidCard(aid: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("本机虚拟卡号（AID）", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.padding(2.dp))
            Text(
                text = aid,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun RecordItem(record: SwipeEvent, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // 用户核心诉求：刷卡日期 + 时间，作为主信息展示
                Text(
                    text = formatTimestamp(record.swipedAt),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(modifier = Modifier.padding(2.dp))
                Text(
                    text = record.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Clear, contentDescription = "删除")
            }
        }
    }
}
