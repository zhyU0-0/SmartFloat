package com.zyy.smartfloat.skills

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.zyy.smartfloat.database.SFTouchEvent
import com.zyy.smartfloat.database.SFTouchEventComposition
import com.zyy.smartfloat.database.SFTouchPosition
import com.zyy.smartfloat.ui.theme.SmartFloatTheme
import com.zyy.smartfloat.skills.viewmodel.TouchDesignViewModel
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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
    //事件设计平台：按屏幕宽高建立坐标系（原点左上角），平台上的操作只覆盖当前事件
    val context = LocalContext.current
    val screenWidth = context.resources.displayMetrics.widthPixels
    val screenHeight = context.resources.displayMetrics.heightPixels
    val currentEvent by viewModel.touchEvent.collectAsState()

    var workspaceSize by remember { mutableStateOf(IntSize.Zero) }
    var buttonPosition by remember { mutableStateOf(Offset(60f, 60f)) }
    var dragPath by remember { mutableStateOf<List<Offset>>(emptyList()) }
    val textMeasurer = rememberTextMeasurer()
    val buttonRadiusPx = with(LocalDensity.current) { 14.dp.toPx() }

    //工作区坐标 -> 屏幕坐标
    fun toScreenPosition(p: Offset): SFTouchPosition {
        if (workspaceSize.width == 0 || workspaceSize.height == 0) return SFTouchPosition(0.0, 0.0)
        return SFTouchPosition(
            (p.x / workspaceSize.width * screenWidth).toDouble(),
            (p.y / workspaceSize.height * screenHeight).toDouble()
        )
    }

    fun clampToWorkspace(p: Offset): Offset {
        val maxX = (workspaceSize.width - buttonRadiusPx * 2).coerceAtLeast(0f)
        val maxY = (workspaceSize.height - buttonRadiusPx * 2).coerceAtLeast(0f)
        return Offset(p.x.coerceIn(0f, maxX), p.y.coerceIn(0f, maxY))
    }

    //屏幕坐标 -> 工作区坐标
    fun toWorkspacePosition(p: SFTouchPosition): Offset {
        if (workspaceSize.width == 0 || workspaceSize.height == 0) return Offset.Zero
        return Offset(
            (p.x / screenWidth * workspaceSize.width).toFloat(),
            (p.y / screenHeight * workspaceSize.height).toFloat()
        )
    }

    //选择事件后，把该事件的数据绘制到设计平台，便于继续编辑
    LaunchedEffect(currentEvent, workspaceSize) {
        if (workspaceSize == IntSize.Zero) return@LaunchedEffect
        dragPath = if (currentEvent.type == 2) {
            (listOf(currentEvent.downPosition) + currentEvent.movePositionList + currentEvent.upPosition)
                .map { toWorkspacePosition(it) }
        } else {
            emptyList()
        }
        val anchor = when (currentEvent.type) {
            1 -> currentEvent.downPosition
            2 -> currentEvent.upPosition
            else -> null
        }
        if (anchor != null) {
            buttonPosition = clampToWorkspace(
                toWorkspacePosition(anchor) - Offset(buttonRadiusPx, buttonRadiusPx)
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(8.dp)
            .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
            .onSizeChanged { workspaceSize = it }
            .pointerInput(workspaceSize, currentEvent.type) {
                when (currentEvent.type) {
                    //点击事件：在平台任意位置点按，按钮出现在点按处并记录坐标
                    1 -> detectTapGestures { tap ->
                        dragPath = emptyList()
                        buttonPosition = clampToWorkspace(tap - Offset(buttonRadiusPx, buttonRadiusPx))
                        val pos = toScreenPosition(tap)
                        viewModel.recordTouchEvent(
                            currentEvent.copy(downPosition = pos, upPosition = pos)
                        )
                    }
                    //滑动事件：在平台任意位置开始拖动，按钮跟随手指，轨迹即实际拖动路径
                    2 -> detectDragGestures(
                        onDragStart = { start ->
                            buttonPosition = clampToWorkspace(start - Offset(buttonRadiusPx, buttonRadiusPx))
                            dragPath = listOf(start)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val p = change.position
                            buttonPosition = clampToWorkspace(p - Offset(buttonRadiusPx, buttonRadiusPx))
                            dragPath = dragPath + p
                        },
                        onDragEnd = {
                            if (dragPath.size >= 2) {
                                viewModel.recordTouchEvent(
                                    currentEvent.copy(
                                        downPosition = toScreenPosition(dragPath.first()),
                                        movePositionList = dragPath
                                            .subList(1, dragPath.size - 1)
                                            .map { toScreenPosition(it) },
                                        upPosition = toScreenPosition(dragPath.last())
                                    )
                                )
                            }
                        },
                        onDragCancel = { dragPath = emptyList() }
                    )
                }
            }
    ) {
        //坐标系网格 + 刻度 + 滑动轨迹
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (workspaceSize == IntSize.Zero) return@Canvas
            val gridColor = Color(0xFFE0E0E0)
            val labelStyle = TextStyle(fontSize = 9.sp, color = Color.Gray)
            for (i in 0..4) {
                val x = size.width * i / 4
                val y = size.height * i / 4
                if (i in 1..3) {
                    drawLine(gridColor, Offset(x, 0f), Offset(x, size.height))
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y))
                }
                drawText(textMeasurer, "${screenWidth * i / 4}", Offset(x + 2f, 2f), labelStyle)
                if (i > 0) {
                    drawText(textMeasurer, "${screenHeight * i / 4}", Offset(2f, y + 2f), labelStyle)
                }
            }
            //只有滑动事件才绘制轨迹：线段 + 每个采样点画朝向下一采样点的三角形，密集程度反映移动快慢
            if (currentEvent.type == 2 && dragPath.size >= 2) {
                val lineColor = Color(0xFF0052D4)
                for (i in 0 until dragPath.size - 1) {
                    val from = dragPath[i]
                    val to = dragPath[i + 1]
                    drawLine(lineColor, from, to, strokeWidth = 2f)
                    val dir = to - from
                    if (dir.getDistance() > 1f) {
                        drawDirectionTriangle(from, atan2(dir.y, dir.x), lineColor)
                    }
                }
            }
        }

        //当前事件类型 + 按钮对应的屏幕坐标
        val typeText = when (currentEvent.type) {
            0 -> "延时"
            1 -> "点击"
            2 -> "滑动"
            else -> ""
        }
        val center = buttonPosition + Offset(buttonRadiusPx, buttonRadiusPx)
        val screenPos = toScreenPosition(center)
        Text(
            text = if (currentEvent.type == 0) {
                "$typeText ${currentEvent.delay}ms"
            } else {
                "$typeText (${screenPos.x.toInt()}, ${screenPos.y.toInt()})"
            },
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
        )

        //位置标记（点击/滑动事件才有），仅用于显示当前事件所在位置
        if (currentEvent.type == 1 || currentEvent.type == 2) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(buttonPosition.x.roundToInt(), buttonPosition.y.roundToInt()) }
                    .size(28.dp)
                    .background(Color(0xFF0052D4), CircleShape)
            )
        }
    }
}

private fun DrawScope.drawDirectionTriangle(at: Offset, angle: Float, color: Color) {
    val r = 12f
    val p1 = Offset(at.x + cos(angle) * r, at.y + sin(angle) * r)
    val p2 = Offset(at.x + cos(angle + 2.6f) * r * 0.6f, at.y + sin(angle + 2.6f) * r * 0.6f)
    val p3 = Offset(at.x + cos(angle - 2.6f) * r * 0.6f, at.y + sin(angle - 2.6f) * r * 0.6f)
    val path = Path().apply {
        moveTo(p1.x, p1.y)
        lineTo(p2.x, p2.y)
        lineTo(p3.x, p3.y)
        close()
    }
    drawPath(path, color)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TouchEventList(
    viewModel: TouchDesignViewModel,
    modifier: Modifier = Modifier
){
    val compositions by viewModel.composition.collectAsState()
    val selectedIndex by viewModel.selectedIndex.collectAsState()
    val touchEventCompositions by viewModel.touchEventCompositions.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var showAddSheet by remember { mutableStateOf(false) }
    var showDelayDialog by remember { mutableStateOf(false) }
    var editingDelayIndex by remember { mutableStateOf(-1) }
    var actionIndex by remember { mutableStateOf(-1) }
    //拖动排序状态
    var draggingIndex by remember { mutableStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    //竖直显示列表，宽100dp，末尾为添加按钮
    Column(modifier = modifier.fillMaxHeight()) {
        Text(
            text = "事件",
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(8.dp)
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(compositions) { index, event ->
                TouchEventItem(
                    event = event,
                    selected = index == selectedIndex,
                    onClick = {
                        if (event.type == 0) {
                            //点击延时事件：选中并弹出弹窗修改时长
                            editingDelayIndex = index
                            showDelayDialog = true
                        } else {
                            viewModel.selectEvent(index)
                        }
                    },
                    onLongClick = { actionIndex = index },
                    modifier = Modifier
                        .zIndex(if (index == draggingIndex) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (index == draggingIndex) dragOffsetY else 0f
                        },
                    dragModifier = Modifier.pointerInput(index, compositions.size) {
                        detectDragGestures(
                            onDragStart = {
                                draggingIndex = index
                                dragOffsetY = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffsetY += amount.y
                            },
                            onDragEnd = {
                                val from = index
                                val info = listState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { it.index == from }
                                val center = info?.let { it.offset + dragOffsetY + it.size / 2f }
                                val to = center?.let { c ->
                                    listState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { c >= it.offset && c <= it.offset + it.size }
                                        ?.index
                                }
                                if (to != null && to != from) viewModel.moveEvent(from, to)
                                draggingIndex = -1
                                dragOffsetY = 0f
                            },
                            onDragCancel = {
                                draggingIndex = -1
                                dragOffsetY = 0f
                            }
                        )
                    }
                )
            }
            item {
                IconButton(onClick = {
                    if (touchEventCompositions.isEmpty()) {
                        //还没有组合时不允许添加事件
                        Toast.makeText(context, "请先创建组合", Toast.LENGTH_SHORT).show()
                    } else {
                        showAddSheet = true
                    }
                }) {
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
                    editingDelayIndex = -1
                    showDelayDialog = true
                }
                SheetOption("点击") {
                    viewModel.newTouchEvent(1)
                    showAddSheet = false
                }
                SheetOption("滑动") {
                    viewModel.newTouchEvent(2)
                    showAddSheet = false
                }
            }
        }
    }

    if (showDelayDialog) {
        DelayInputDialog(
            initialValue = compositions.getOrNull(editingDelayIndex)
                ?.takeIf { it.type == 0 && it.delay > 0 }
                ?.delay?.toString() ?: "",
            onConfirm = { delay ->
                if (editingDelayIndex >= 0) {
                    viewModel.updateDelay(editingDelayIndex, delay)
                } else {
                    viewModel.addTouchEvent(viewModel.getEmptyTouchEvent().copy(type = 0, delay = delay))
                }
                showDelayDialog = false
                editingDelayIndex = -1
            },
            onDismiss = {
                showDelayDialog = false
                editingDelayIndex = -1
            }
        )
    }

    if (actionIndex >= 0) {
        ModalBottomSheet(onDismissRequest = { actionIndex = -1 }) {
            Column(modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)) {
                SheetOption("删除") {
                    viewModel.deleteEvent(actionIndex)
                    actionIndex = -1
                }
                SheetOption("复制") {
                    viewModel.copyEvent(actionIndex)
                    actionIndex = -1
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompositionList(
    viewModel: TouchDesignViewModel,
    modifier: Modifier = Modifier
){
    val touchEventCompositions by viewModel.touchEventCompositions.collectAsState()
    val selectedCompositionIndex by viewModel.selectedCompositionIndex.collectAsState()
    var showNameDialog by remember { mutableStateOf(false) }
    var compositionActionIndex by remember { mutableStateOf(-1) }
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
                CompositionItem(
                    index = index,
                    composition = composition,
                    selected = index == selectedCompositionIndex,
                    onClick = { viewModel.selectComposition(index) },
                    onLongClick = { compositionActionIndex = index }
                )
            }
            item {
                IconButton(onClick = { showNameDialog = true }) {
                    Text(text = "+", fontSize = 24.sp, color = Color(0xFF0052D4))
                }
            }
        }
        TextButton(onClick = { viewModel.saveCompositions() }) {
            Text("保存")
        }
    }

    if (showNameDialog) {
        CompositionNameDialog(
            onConfirm = { name ->
                viewModel.addComposition(name)
                showNameDialog = false
            },
            onDismiss = { showNameDialog = false }
        )
    }

    if (compositionActionIndex >= 0) {
        ModalBottomSheet(onDismissRequest = { compositionActionIndex = -1 }) {
            Column(modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)) {
                SheetOption("删除") {
                    viewModel.deleteComposition(compositionActionIndex)
                    compositionActionIndex = -1
                }
            }
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
private fun DelayInputDialog(
    initialValue: String = "",
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var input by remember(initialValue) { mutableStateOf(initialValue) }
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
private fun CompositionNameDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var input by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建组合") },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("组合名称") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(input) }) {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TouchEventItem(
    event: SFTouchEvent,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    dragModifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Color(0xFFD6E4FF) else Color(0xFFF5F5F5)
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            //拖动把手：按住上下拖动可调整事件顺序
            Box(
                modifier = dragModifier.size(width = 14.dp, height = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "≡", fontSize = 10.sp, color = Color.Gray)
            }
            Text(
                text = touchEventText(event),
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .padding(vertical = 6.dp, horizontal = 2.dp)
                    .weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompositionItem(
    index: Int,
    composition: SFTouchEventComposition,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Color(0xFFD6E4FF) else Color(0xFFF5F5F5)
        ),
        modifier = Modifier
            .fillMaxHeight()
            .width(120.dp)
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = composition.name.ifEmpty { "组合 ${index + 1}" },
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
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
