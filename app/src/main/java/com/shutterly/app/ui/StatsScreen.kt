package com.shutterly.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.shutterly.app.data.Category
import com.shutterly.app.data.Money
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(vm: RecordViewModel, onBack: () -> Unit) {
    val monthRecords by vm.monthRecords.collectAsState()
    val monthOffset by vm.monthOffset.collectAsState()
    val month = LocalDate.now().plusMonths(monthOffset.toLong())
    val total = monthRecords.sumOf { it.amountFen }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("月度统计") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { vm.monthOffset.value -= 1 }) {
                    Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "上一月")
                }
                Text("${month.year}年${month.monthValue}月", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { vm.monthOffset.value += 1 }) {
                    Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "下一月")
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "总支出",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "¥${Money.fenToYuan(total)}",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    val byCategory = Category.entries
                        .map { c -> c to monthRecords.filter { it.category == c.name }.sumOf { it.amountFen } }
                        .sortedByDescending { it.second }
                    byCategory.forEach { (c, sum) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(c.color())
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(c.displayName, Modifier.weight(1f))
                            Text(
                                if (sum > 0) "¥${Money.fenToYuan(sum)}" else "—",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }

            if (monthRecords.isEmpty()) {
                Text(
                    "本月暂无记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(16.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
