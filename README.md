# SmartFloat - 智能悬浮窗助手

一款基于大语言模型的Android智能悬浮窗应用，通过语音命令实现自动化操作。
## 📱 应用截图

<div align="center">

| | | | |
|:---:|:---:|:---:|:---:|
| ![截图1](https://github.com/user-attachments/assets/d364604a-151c-4d1a-a5fa-475e9a6b7f36) | ![截图2](https://github.com/user-attachments/assets/d8bfa7ac-4a4a-45fb-a3b8-781fac62a2e2) |![截图3](https://github.com/user-attachments/assets/e1e4cf75-2748-410a-aa50-cf309ce9bc90) | ![截图4](https://github.com/user-attachments/assets/eac45008-e8ba-4a47-bbff-ca73a1003aab) |

</div>

## 功能特点

- 🎤 **语音控制**：通过语音输入命令，无需手动操作
- 🤖 **AI驱动**：集成大语言模型，智能理解用户意图
- ⚡ **高效执行**：结合OCR技术，快速识别屏幕内容并执行操作
- 🎯 **精准定位**：支持多次点击校准，提高操作准确性
- 🔄 **任务管理**：支持任务打断和状态管理

## 技术栈

- **语言**：Kotlin
- **框架**：Android Jetpack (ViewModel, Room)
- **网络**：Retrofit
- **OCR**：MLKit
- **语音识别**：腾讯云ASR
- **UI**：悬浮窗服务

# 屏幕信息获取方式的选择
## 1 直接截图发送给AI
一开始想直接截图发送给AI，告诉图片的长宽和坐标原点位置，等大模型返回点击坐标就行，结果第一次就花了50多秒钟才收到响应，而且点击的坐标也有和大偏差。

后来就在图片上用Canvas画上坐标的4个点还有一些坐标线，让大模型能够根据坐标线来定位到真正想点击的位置。虽然有用，但是还是有一部分点击偏差比较大。

在prompt里面添加了一个history字段，用来记录AI的操作和点击坐标，再反过来发回给大模型，并且在图片上上一次的点击的位置绘制一个浅红色的小点，让大模型知道自己上一次操作的细节，以此来调整下一次点击

最终成果可以顶着超高的响应时长，完成

打开QQ，点击头像，打开相册；

打开原神，进入游戏，点击进入人物界面，点击武器详情页

的功能，大概每个点击之间都要消耗2000Token和30秒的等待时间，对于游戏界面这样复杂的环境，点击坐标的偏差会更大，往往需要点击3次才能正确定位坐标，而且是不考虑错误点击影响的情况。

## 2 使用无障碍获取屏幕节点
尝试使用无障碍服务来获取屏幕内能点击的节点和节点的坐标，然后通过文本的形式发送给大模型代替图片。

但是现实没有想象的那么美好，我遍历出可点击的节点后，发现有很多组件都无法获取到真实显示的内容，而且对于`webView`和`gameView`就只能是获取到外围的一个大组件。
内部的内容无法读取，这个方案的实用性就很差。

最终被放弃了

## 3 OCR识别文本和坐标
既然直接读取图片太慢，那我就在本地使用OCR先把截图里面的文本和文本坐标扫描出来，通过文本的形式发送给大模型。我使用的是`MLKit`。
终于，对于文本环境，这个方案非常实用，完全可以做到自动完成选择题，或者是打开一些软件之类的任务。

这个方案在测试时，可以完成形式与政策课程的线上考试，都是选择题，在使用deepseek-v4-flash的情况下，每轮循环消耗Token600-800，耗时1-2秒，且缓存命中率有40%-50%，
是目前测试中表现最好的方案

# Token减少方法
## 1 解除深度思考模式
首先，在发送请求时把`thinking`的`type`设置为`disabled`
```kotlin
request = LlmRequest(
                            model = model,
                            messages = listOf(
                                LlmMessage(
                                    role = "user",
                                    content = listOf(
                                        LlmContent(
                                            type = "text",
                                            text = gson.toJson(llmBody)
                                        )
                                    )
                                )
                            ),
                            thinking = LLmThinkingType("disabled")
                        )
```
这个可以让模型不适应深度思考模式，因为这个系统并不注重模型输出了什么，所以不需要。

在设置以后，能显著减少输出消耗的Token，大概能从1000减少到100以内，稳定在70左右，可以说减少了90%的输出Token消耗
## 2 增加token的缓存命中率
然后，对于大模型计算 token 缓存命中，最核心的逻辑就是`前缀匹配`
我们要在发送的消息中，尽量把不变的信息放在前面，变化的信息放在后面，比如提示词和页面坐标就是不变的。
在构建请求体的时候，我使用的是Gson将内容直接转换成JSON的字符串。
```kotlin
data class LLmBody(
    val a_prompt: String,
    val b_question: String,
    val c_maxX: Int,
    val c_maxY: Int,
    val d_history: List<ProcessHistory>,
    var z_content: String?
)
```
由于Gson在转换字符串时，是使用元素的变量名首字母进行排序，我就在原来的基础上给每个变量名都强行添加了一个首字母，让提示词出现在前面。

或者不使用Gson,我们可以自己拼接，将prompt放在前面也是可以的。

如此操作下来，成功将缓存命中率从0%提升到40%左右，响应速度也快了不少。
# 返回格式的设置
```kotlin
data class LLmResponse(
    val tapPoints: List<TapPoints>,//用来记录点击细节，可以一次执行多次点击
    val command: String?,//用户最开始的命令，保证不在任务中途出现太多幻觉或者忘记一开始的目标
    val isEnd: Boolean,//用于表示任务是否结束，未结束就要在点击后重新获取屏幕信息，再次发起下一次请求
    val remark: String//表名在这一步操作中，大模型做了什么，后续会记录在history里面，让大模型参考
)
data class TapPoints(
    val tapX: Double,
    val tapY: Double,
    val delay: Int,//在返回后，点击执行的延时
)
```


# 任务打断机制
一开始想要设置一个Bool值来控制任务的开始与停止，但是如果在等待响应的途中按下了停止，然后在上一次还没有响应之前就又开始下一次任务，结果就会混杂两次任务的结果，导致混乱

现在使用的方案是给每个任务都设置一个单独的ID，停顿了就把ID增加1，在收到返回时会先校验任务ID，如果不相同就直接丢弃，以此来做到打断任务。
# 悬浮按钮的状态介绍
悬浮按钮主要分为，未录音 编辑命令 执行中 三个状态，  

1. 未录音状态显示一个麦克风，手指不移动长按后就会触发录音变成红色，松手就会结束录音，并进入编辑命令状态。

2. 编辑命令状态会在悬浮窗按钮从左到右显示上传按钮，命令内容，编辑按钮，和删除按钮，
点击删除按钮会丢弃识别结果回到未录音状态，点击上传按钮会读取命令并开始执行任务，进入执行中状态。 
编辑按钮可修改语音识别结果，支持手动输入命令。

3. 执行中状态显示暂停按钮，在原地点击后就会暂停任务。

### 细节
在未录音状态时，手指放到按钮上会读秒，会有一个500毫秒的检查，当超过时，就会开始录音

在录音或者执行中状态，会在手指送开按钮时做判断，如果是录音中，就会停止录音，如果松开时的坐标与按下坐标的x和y差值小于10（不是移动按钮行为），就会打断任务

### 状态转换流程

```
未录音 →(长按录音)→ 录音中 →(松手)→ 编辑命令
                            ↓(点击删除)
                        未录音
         ↓(点击上传)
执行中 →(完成/打断)→ 未录音
```
# 模型的分享与接收
通过读取和编辑剪切板，实现了模型的分析与接收。
```kotlin
fun getClipboardContent(context: Context): String {//读取剪切板
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = clipboard.primaryClip

        if (clipData != null && clipData.itemCount > 0) {
            val item = clipData.getItemAt(0)
            Log.d("MainActivity getClipboardContent", item.toString())
            return item.text?.toString() ?: ""
        } else {
            Log.d("MainActivity getClipboardContent", "null")
        }
        return ""
    }

    fun copyAddMode(context: Context, addModel: AddModel) {//添加到剪切板
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val oldClip = clipboard.primaryClip
        val gson = Gson()
        val addModeText = gson.toJson(addModel)
        val newClip = ClipData.newPlainText("add_model", addModeText)
        if (oldClip != null) {
            for (i in 0 until oldClip.itemCount) {
                newClip.addItem(oldClip.getItemAt(i))
            }
        }
        Toast.makeText(context, "复制成功", Toast.LENGTH_SHORT).show()
        clipboard.setPrimaryClip(newClip)
    }
```
```kotlin
data class AddModel(
    var modelName: String,
    var apiKey: String,
    var baseUrl: String
)
```
在添加模型弹窗有一个剪切板按钮，点击后会读取剪切板里面第一条信息，如果是：
```json
{
  "apiKey":"xxxxxx",
  "baseUrl":"https://xxx/xxx",
  "modelName":"xxx-v1.0-xxx"
}
```
就会转换成`AddMode`对象，然后直接填充到添加弹窗的输入框里面。

在编辑模型弹窗放了个复制按钮，点击后就把模型转换成AddMode对象然后变成Json放到剪切板第一条。用户能直接复制分享给朋友。

不过这个方法在传递apiKey这种敏感信息时，可能会泄露，后续考虑增添加密算法和一键分享。

# 现有问题

1. 

# 项目结构

```
smartfloat/
├── app/
│   ├── src/main/java/com/zyy/smartfloat/
│   │   ├── database/          # 数据库相关
│   │   ├── network/           # 网络请求
│   │   ├── prompt/            # 提示词管理
│   │   ├── service/           # 服务类（悬浮窗、无障碍）
│   │   ├── utils/             # 工具类
│   │   └── viewmodel/         # ViewModel层
│   └── src/main/res/
│       ├── raw/               # 提示词资源
│       └── xml/               # 配置文件
└── README.md
```

# 使用说明

1. 安装应用并授予录音、悬浮窗和无障碍权限、配置模型
2. 长按悬浮按钮开始录音
3. 语音输入命令后松手
4. 可编辑识别结果后点击上传执行
5. 等待AI执行完成

# end




