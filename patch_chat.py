import sys

path = "app/src/main/java/com/example/ui/chat/ChatScreen.kt"
with open(path, "r") as f:
    content = f.read()

search = """    LaunchedEffect(lastMessage?.messageId) {
        if (lastMessage != null && lastMessage.role == "assistant" && ttsEnabled && !lastMessage.isError) {
            ttsManager.speak(
                lastMessage.messageId,
                lastMessage.content,
                currentPersona.voice ?: ""
            )
        }
    }"""

replace = """    LaunchedEffect(lastMessage?.messageId) {
        if (lastMessage != null && lastMessage.role == "assistant" && ttsEnabled && !lastMessage.isError) {
            kotlinx.coroutines.delay(100) // allow DB persona updates to propagate
            ttsManager.speak(
                lastMessage.messageId,
                lastMessage.content,
                currentPersona.voice ?: ""
            )
        }
    }"""

content = content.replace(search, replace)
with open(path, "w") as f:
    f.write(content)
