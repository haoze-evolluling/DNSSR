package com.haoze.dnssr.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.haoze.dnssr.R
import com.haoze.dnssr.ui.components.SettingsInfoText
import com.haoze.dnssr.ui.components.SettingsScaffold

private val CO_BUILDERS = listOf(
    RecognitionMember(
        name = "alone",
        avatarRes = R.drawable.alone_avatar,
        acknowledgement = "感谢为 DNSSR 提出建议与帮助测试"
    ),
    RecognitionMember(
        name = "酷安@睿上源",
        avatarRes = R.drawable.ruishangyuan_avatar,
        acknowledgement = "感谢为 DNSSR 提出建议与帮助测试"
    ),
    RecognitionMember(
        name = "酷安@widiOA",
        avatarRes = R.drawable.widioa_avatar,
        acknowledgement = "感谢为 DNSSR 提出建议与帮助测试"
    ),
    RecognitionMember(
        name = "酷安@妄炁",
        avatarRes = R.drawable.wangqi_avatar,
        acknowledgement = "感谢为 DNSSR 提出建议与帮助测试"
    )
)

@Composable
fun CoBuilderListScreen(
    onBack: () -> Unit,
    title: String = "共建者名单"
) {
    SettingsScaffold(title = title, onBack = onBack) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsInfoText(
                text = "感谢每一位为 DNSSR 提出建议、帮助测试的共建者！",
                modifier = Modifier.padding(top = 8.dp)
            )
            RecognitionList(
                members = CO_BUILDERS,
                emptyText = "暂时还没有共建者，期待在这里写下你的名字。"
            )
        }
    }
}
