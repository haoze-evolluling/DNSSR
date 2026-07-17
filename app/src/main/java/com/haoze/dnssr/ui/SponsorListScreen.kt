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

private val SPONSORS = listOf(
    RecognitionMember(
        name = "alone",
        avatarRes = R.drawable.alone_avatar,
        acknowledgement = "感谢对 DNSSR 项目的赞助支持"
    ),
    RecognitionMember(
        name = "睿上源",
        avatarRes = R.drawable.ruishangyuan_avatar,
        acknowledgement = "感谢对 DNSSR 项目的赞助支持"
    ),
    RecognitionMember(
        name = "理塘丁真",
        avatarRes = R.drawable.litangdingzhen_avatar,
        acknowledgement = "感谢对 DNSSR 项目的赞助支持"
    ),
    RecognitionMember(
        name = "天涯浮客",
        avatarRes = R.drawable.tianyafuke_avatar,
        acknowledgement = "感谢对 DNSSR 项目的赞助支持"
    ),
    RecognitionMember(
        name = "AceTaffy1883",
        avatarRes = R.drawable.acetaffy1883_avatar,
        acknowledgement = "感谢对 DNSSR 项目的赞助支持"
    ),
    RecognitionMember(
        name = "心疼头头哥",
        avatarRes = R.drawable.xintengtoutouge_avatar,
        acknowledgement = "感谢对 DNSSR 项目的赞助支持"
    ),
    RecognitionMember(
        name = "恐龙复生",
        avatarRes = R.drawable.konglongfusheng_avatar,
        acknowledgement = "感谢对 DNSSR 项目的赞助支持"
    )
)

@Composable
fun SponsorListScreen(
    onBack: () -> Unit,
    title: String = "赞助者名单"
) {
    SettingsScaffold(title = title, onBack = onBack) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsInfoText(
                text = "感谢每一位支持 DNSSR 项目的朋友！名单仅按赞助时间顺序排列，与赞助金额无关；每一份支持都同样珍贵。",
                modifier = Modifier.padding(top = 8.dp)
            )
            RecognitionList(
                members = SPONSORS,
                emptyText = "暂时还没有赞助者，期待在这里写下你的名字。"
            )
        }
    }
}
