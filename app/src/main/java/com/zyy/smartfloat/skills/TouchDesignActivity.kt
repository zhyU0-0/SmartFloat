package com.zyy.smartfloat.skills

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyy.smartfloat.database.SFTouchEvent
import com.zyy.smartfloat.database.SFTouchEventComposition
import com.zyy.smartfloat.skills.viewmodel.TouchDesignViewModel
import com.zyy.smartfloat.ui.theme.SmartFloatTheme

class TouchDesignActivity : ComponentActivity() {
    private val viewModel: TouchDesignViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmartFloatTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    TouchDesignPage(
                        modifier = Modifier.padding(innerPadding),
                        viewModel = viewModel
                        )
                }
            }
        }
    }
}
@Composable
fun TouchDesignPage(
    modifier: Modifier = Modifier,
    viewModel: TouchDesignViewModel
){
    Column(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier
            .fillMaxWidth()
            .weight(1f)) {
            DesignWorkspace(viewModel, Modifier.weight(1f))
            TouchEventList(viewModel, Modifier.width(100.dp))
        }
        CompositionList(viewModel, Modifier.height(80.dp))
    }
}

@Composable
fun DesignWorkspace(
    viewModel: TouchDesignViewModel,
    modifier: Modifier = Modifier
){
    //事件设计平台，先使用空白代替
    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(8.dp)
            .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TouchEventList(
    viewModel: TouchDesignViewModel,
    modifier: Modifier = Modifier
){
    val compositions by viewModel.composition.collectAsState()
    var showAddSheet by remember { mutableStateOf(false) }
    var showDelayDialog by remember { mutableStateOf(false) }
    //竖直显示列表，宽100dp，末尾为添加按钮
    Column(modifier = modifier.fillMaxHeight()) {
        Text(
            text = "事件",
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(8.dp)
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(compositions) { event ->
                TouchEventItem(event)
            }
            item {
                IconButton(onClick = { showAddSheet = true }) {
                    Text(text = "+", fontSize = 24.sp, color = Color(0xFF0052D4))
                }
            }
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(onDismissRequest = { showAddSheet = false }) {
            Column(modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)) {
                SheetOption("延时") {
                    showAddSheet = false
                    showDelayDialog = true
                }
                SheetOption("点击") {
                    viewModel.addTouchEvent(viewModel.getEmptyTouchEvent().copy(type = 1))
                    showAddSheet = false
                }
                SheetOption("滑动") {
                    viewModel.addTouchEvent(viewModel.getEmptyTouchEvent().copy(type = 2))
                    showAddSheet = false
                }
            }
        }
    }

    if (showDelayDialog) {
        DelayInputDialog(
            onConfirm = { delay ->
                viewModel.addTouchEvent(viewModel.getEmptyTouchEvent().copy(type = 0, delay = delay))
                showDelayDialog = false
            },
            onDismiss = { showDelayDialog = false }
        )
    }
}

@Composable
fun CompositionList(
    viewModel: TouchDesignViewModel,
    modifier: Modifier = Modifier
){
    val touchEventCompositions by viewModel.touchEventCompositions.collectAsState()
    //横向显示列表，高80dp，末尾为添加按钮，右侧为保存按钮
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(touchEventCompositions) { index, composition ->
                CompositionItem(index, composition, onClick = { viewModel.selectComposition(index) })
            }
            item {
                IconButton(onClick = { viewModel.addComposition() }) {
                    Text(text = "+", fontSize = 24.sp, color = Color(0xFF0052D4))
                }
            }
        }
        TextButton(onClick = { viewModel.saveCompositions() }) {
            Text("保存")
        }
    }
}

@Composable
private fun SheetOption(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(text = text, fontSize = 16.sp)
    }
}

@Composable
private fun DelayInputDialog(onConfirm: (Long) -> Unit, onDismiss: () -> Unit) {
    var input by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置延时时长") },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.filter(Char::isDigit) },
                label = { Text("延时时长(ms)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { input.toLongOrNull()?.let(onConfirm) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun TouchEventItem(event: SFTouchEvent) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            text = touchEventText(event),
            fontSize = 12.sp,
            modifier = Modifier.padding(6.dp)
        )
    }
}

@Composable
private fun CompositionItem(index: Int, composition: SFTouchEventComposition, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxHeight()
            .width(120.dp)
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(text = "组合 ${index + 1}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(text = "${composition.composition.size} 个事件", fontSize = 12.sp, color = Color.Gray)
        }
    }
}

private fun touchEventText(event: SFTouchEvent): String = when (event.type) {
    0 -> "延迟 ${event.delay}ms"
    1 -> "点击 (${event.downPosition.x.toInt()}, ${event.downPosition.y.toInt()})"
    2 -> "滑动 (${event.downPosition.x.toInt()}, ${event.downPosition.y.toInt()}) → (${event.upPosition.x.toInt()}, ${event.upPosition.y.toInt()})"
    else -> "未知事件"
}
