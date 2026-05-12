package com.zyy.smartfloat

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyy.smartfloat.database.Conversation
import com.zyy.smartfloat.database.DailyTokenUsage
import com.zyy.smartfloat.ui.theme.SmartFloatTheme
import kotlinx.coroutines.launch

import java.text.SimpleDateFormat
import java.util.*

class StatisticsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmartFloatTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    StatisticsScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun StatisticsScreen(
    modifier: Modifier = Modifier,
    viewModel: StatisticsViewModel = viewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.loadData()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "统计页面",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // 总 Token 使用量卡片
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "总 Token 使用量",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${viewModel.totalTokenUsed}",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // 每日 Token 使用条形图
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "近 30 天 Token 使用",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (viewModel.dailyTokenUsage.isNotEmpty()) {
                    TokenUsageBarChart(
                        dailyUsage = viewModel.dailyTokenUsage.reversed(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无数据")
                    }
                }
            }
        }

        // 对话历史列表
        Text(
            text = "对话历史",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(viewModel.allConversations) { conversation ->
                ConversationItem(conversation = conversation)
            }
        }
    }
}

class StatisticsViewModel : ViewModel() {
    private val repository = MyApp.repository

    var allConversations by mutableStateOf<List<Conversation>>(emptyList())
        private set

    var dailyTokenUsage by mutableStateOf<List<DailyTokenUsage>>(emptyList())
        private set

    var totalTokenUsed by mutableStateOf(0)
        private set

    init {
        viewModelScope.launch {
            repository.allConversations.collect { list ->
                allConversations = list
            }
        }
        
        viewModelScope.launch {
            repository.dailyTokenUsage.collect { list ->
                dailyTokenUsage = list
            }
        }
        
        viewModelScope.launch {
            repository.totalTokenUsed.collect { total ->
                Log.d("total1", total.toString())
                totalTokenUsed = total
            }
        }
    }

    fun loadData() {
        viewModelScope.launch {
            repository.refreshData()
        }
    }
}

@Composable
fun TokenUsageBarChart(
    dailyUsage: List<DailyTokenUsage>,
    modifier: Modifier = Modifier
) {
    if (dailyUsage.isEmpty()) return

    val maxToken = dailyUsage.maxOfOrNull { it.totalToken } ?: 1

    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val barCount = dailyUsage.size
        val barWidth = (canvasWidth - 32.dp.toPx() * 2) / barCount
        val barSpacing = 8.dp.toPx()

        dailyUsage.forEachIndexed { index, usage ->
            val barHeight = (usage.totalToken.toFloat() / maxToken) * (canvasHeight - 60.dp.toPx())
            val left = 16.dp.toPx() + index * (barWidth + barSpacing)
            val top = canvasHeight - 40.dp.toPx() - barHeight

            // 绘制条形
            drawRect(
                color = Color(0xFF4CAF50),
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(barWidth - barSpacing, barHeight)
            )

            // 绘制日期标签
            val textPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
                textSize = 24f
                textAlign = android.graphics.Paint.Align.CENTER
            }
            drawContext.canvas.nativeCanvas.drawText(
                usage.date.takeLast(5),
                left + (barWidth - barSpacing) / 2,
                canvasHeight - 10.dp.toPx(),
                textPaint
            )
        }
    }
}

@Composable
fun ConversationItem(conversation: Conversation) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Input: ${conversation.inputToken} | Output: ${conversation.outputToken}",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "耗时: ${conversation.answerTime}ms",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = conversation.remark,
                fontSize = 14.sp,
                maxLines = 3,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "模型: ${conversation.modelName}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Text(
                    text = dateFormat.format(Date(conversation.createdAt)),
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}
