package com.hqcard.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hqcard.hce.EMULATED_AID
import com.hqcard.nfc.NfcAvailability
import com.hqcard.record.SwipeEvent
import com.hqcard.record.SwipeEventRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 主界面状态。契约：modules/app_shell/docs/interface.md */
data class MainUiState(
    val nfcAvailability: NfcAvailability,
    /** 本机模拟的虚拟卡 AID（展示给用户） */
    val emulatedAid: String,
    /** 小米钱包刷卡侦测（无障碍服务）是否已开启 */
    val detectorEnabled: Boolean,
    val records: List<SwipeEvent>,
)

/**
 * 主界面 ViewModel。
 *
 * 注：原「手机主动读卡」接线已按需求停用（代码保留在 modules/nfc，未再引用）。
 * 刷卡事件的写入由 hce 模块的 EmulatedCardService（读卡器选中本机虚拟卡）
 * 与 detector 模块的 WalletSwipeDetectorService（小米钱包界面唤出）完成，
 * 本 ViewModel 只负责观察数据库并刷新界面。
 */
class MainViewModel(
    private val repository: SwipeEventRepository,
    private val availabilityProvider: () -> NfcAvailability,
    private val detectorStatusProvider: () -> Boolean,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MainUiState(
            nfcAvailability = availabilityProvider(),
            emulatedAid = EMULATED_AID,
            detectorEnabled = detectorStatusProvider(),
            records = emptyList(),
        ),
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        // 记录列表实时同步（服务写入 → Room Flow → 界面）
        viewModelScope.launch {
            repository.observeAll().collect { records ->
                _uiState.update { it.copy(records = records) }
            }
        }
    }

    /** 回到前台时刷新 NFC 与无障碍侦测状态（用户可能刚从系统设置回来） */
    fun onForeground() {
        _uiState.update {
            it.copy(
                nfcAvailability = availabilityProvider(),
                detectorEnabled = detectorStatusProvider(),
            )
        }
    }

    fun deleteRecord(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun clearRecords() {
        viewModelScope.launch { repository.clear() }
    }
}
