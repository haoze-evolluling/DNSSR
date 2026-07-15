package com.haoze.dnssr.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.haoze.dnssr.R
import com.haoze.dnssr.ui.components.SettingsDivider
import com.haoze.dnssr.ui.components.SettingsGroup
import com.haoze.dnssr.ui.components.SettingsGroupTitle
import com.haoze.dnssr.ui.components.SettingsInfoText
import com.haoze.dnssr.ui.components.SettingsScaffold

private const val PROJECT_REPOSITORY_URL = "https://github.com/haoze-evolluling/DNSSR"

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    title: String = "应用信息"
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    SettingsScaffold(
        title = title,
        onBack = onBack
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            SettingsInfoText(
                text = "DNSSR 是一款运行在 Android 设备本机的 DNS 解析管理工具。它接管系统 DNS 查询，并按配置转发至 DNS、DoH、DoH3 或 DoT 上游服务。",
                modifier = Modifier.padding(top = 8.dp)
            )

            SettingsGroupTitle("软件定位")
            SettingsGroup {
                AboutText(
                    "DNSSR 通过 Android VpnService 建立仅处理 DNS 的本地通道，不代理普通网页、应用数据或文件传输流量。启用服务时，Android 会要求授予 VPN 权限。"
                )
            }

            SettingsGroupTitle("核心能力")
            SettingsGroup {
                AboutBulletList(
                    items = listOf(
                        "支持 DNS、DoH、DoH3 与 DoT 服务商，并可添加和管理自定义服务。",
                        "支持单服务商、健康权重择优、并行竞速和主备容灾四种解析模式。",
                        "支持可配置 DNS 缓存，减少重复查询，并提供缓存记录和命中情况。",
                        "支持屏蔽规则、白名单规则和 AdGuard DNS 规则订阅，可设置订阅自动更新。",
                        "支持 Bootstrap IP，降低解析 DoH、DoH3 或 DoT 服务商域名时对系统 DNS 的依赖。",
                        "提供 DNS 请求日志、竞速统计、服务商健康状态和 Bootstrap 统计，便于排查解析状态。",
                        "支持自定义服务与规则订阅的配置导入、导出，以及系统快捷设置磁贴。"
                    )
                )
            }

            SettingsGroupTitle("使用效果")
            SettingsGroup {
                AboutText(
                    "启用后，应用会将设备 DNS 查询交给已配置的上游服务处理。实际解析速度、稳定性和可用性取决于网络环境、所选服务商和本机规则配置；DoH3 使用 QUIC，需要网络允许 UDP/443。"
                )
            }

            SettingsGroupTitle("隐私与本地运行")
            SettingsGroup {
                AboutText(
                    "DNS 缓存、请求日志、规则和服务商配置保存在设备本机。上游 DNS 服务商仍会收到必要的域名解析请求；仅 DoH、DoH3 和 DoT 会对设备到上游之间的 DNS 传输进行加密，普通 DNS 不提供该保护。"
                )
            }

            SettingsGroupTitle("作者")
            SettingsGroup {
                AboutInfoItem(
                    title = "haoze-evolluling",
                    subtitle = "-一位集美大学人工智能系大三学子"
                )
            }

            SettingsGroupTitle("项目仓库")
            SettingsGroup {
                GitHubRepositoryItem(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_REPOSITORY_URL))
                        runCatching {
                            context.startActivity(intent)
                        }.onFailure {
                            Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                SettingsDivider()
                AboutText(PROJECT_REPOSITORY_URL)
            }
        }
    }
}

@Composable
private fun AboutText(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun AboutBulletList(
    items: List<String>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "-",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun AboutInfoItem(
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GitHubRepositoryItem(
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_github),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "haoze-evolluling/DNSSR",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
