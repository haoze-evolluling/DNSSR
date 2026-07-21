package com.haoze.dnssr.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.haoze.dnssr.R
import com.haoze.dnssr.ui.components.SettingsInfoText
import com.haoze.dnssr.ui.components.SettingsScaffold

private val SPONSORS = listOf(
    RecognitionMember(
        name = "alone",
        avatarRes = R.drawable.alone_avatar,
        acknowledgement = "感谢您对 DNSSR 项目的赞助支持"
    ),
    RecognitionMember(
        name = "睿上源",
        avatarRes = R.drawable.ruishangyuan_avatar,
        acknowledgement = "感谢您对 DNSSR 项目的赞助支持"
    ),
    RecognitionMember(
        name = "理塘丁真",
        avatarRes = R.drawable.litangdingzhen_avatar,
        acknowledgement = "感谢您对 DNSSR 项目的赞助支持"
    ),
    RecognitionMember(
        name = "天涯浮客",
        avatarRes = R.drawable.tianyafuke_avatar,
        acknowledgement = "感谢您对 DNSSR 项目的赞助支持"
    ),
    RecognitionMember(
        name = "AceTaffy1883",
        avatarRes = R.drawable.acetaffy1883_avatar,
        acknowledgement = "感谢您对 DNSSR 项目的赞助支持"
    ),
    RecognitionMember(
        name = "心疼头头哥",
        avatarRes = R.drawable.xintengtoutouge_avatar,
        acknowledgement = "感谢您对 DNSSR 项目的赞助支持"
    ),
    RecognitionMember(
        name = "恐龙复生",
        avatarRes = R.drawable.konglongfusheng_avatar,
        acknowledgement = "感谢您对 DNSSR 项目的赞助支持"
    ),
    RecognitionMember(
        name = "xo人头马",
        avatarRes = R.drawable.xorentouma_avatar,
        acknowledgement = "感谢您对 DNSSR 项目的赞助支持"
    ),
    RecognitionMember(
        name = "过江龙傲天",
        avatarRes = R.drawable.guojianglongaotian_avatar,
        acknowledgement = "感谢您对 DNSSR 项目的赞助支持"
    )
)

@Composable
fun SponsorListScreen(
    onBack: () -> Unit,
    title: String = "赞助者名单"
) {
    val context = LocalContext.current
    var newestFirst by remember { mutableStateOf(false) }
    val displayedSponsors = if (newestFirst) SPONSORS.asReversed() else SPONSORS

    SettingsScaffold(
        title = title,
        onBack = onBack,
        actions = {
            IconButton(onClick = {
                newestFirst = !newestFirst
                Toast.makeText(
                    context,
                    if (newestFirst) "当前按赞助时间由晚到早排列" else "当前按赞助时间由早到晚排列",
                    Toast.LENGTH_SHORT
                ).show()
            }) {
                Icon(
                    imageVector = Icons.Default.SwapVert,
                    contentDescription = if (newestFirst) "当前按赞助时间由晚到早排列，点击切换为由早到晚" else "当前按赞助时间由早到晚排列，点击切换为由晚到早"
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsInfoText(
                text = "感谢每一位支持 DNSSR 项目的朋友！名单默认按赞助时间由早到晚排列，可通过右上角按钮切换为由晚到早；与赞助金额无关，每一份支持都同样珍贵。",
                modifier = Modifier.padding(top = 8.dp)
            )
            RecognitionList(
                members = displayedSponsors,
                emptyText = "暂时还没有赞助者，期待在这里写下你的名字。"
            )
        }
    }
}
