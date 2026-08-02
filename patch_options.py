import sys

path = "app/src/main/java/com/example/ui/personas/CreatePersonaSheet.kt"
with open(path, "r") as f:
    content = f.read()

search = 'val voiceOptions = listOf("Magpie-Multilingual.HI-IN.Phung.Neutral", "Magpie-Multilingual.HI-IN.Jason.Neutral", "Magpie-Multilingual.HI-IN.Mia.Neutral", "Magpie-Multilingual.HI-IN.Aria.Neutral", "Magpie-Multilingual.HI-IN.Leo.Neutral", "Magpie-Multilingual.HI-IN.Isabela.Neutral")\n    var voice by remember { mutableStateOf(if (existingPersona?.voice.isNullOrBlank()) "Magpie-Multilingual.HI-IN.Jason.Neutral" else existingPersona?.voice ?: "Magpie-Multilingual.HI-IN.Jason.Neutral") }'

replacement = '''val voiceOptions = listOf("Magpie-Multilingual.HI-IN.Phung", "Magpie-Multilingual.HI-IN.Jason", "Magpie-Multilingual.HI-IN.Mia", "Magpie-Multilingual.HI-IN.Aria", "Magpie-Multilingual.HI-IN.Leo", "Magpie-Multilingual.HI-IN.Isabela", "Magpie-Multilingual.HI-IN.Ray", "Magpie-Multilingual.HI-IN.Long", "Magpie-Multilingual.HI-IN.Sofia", "Magpie-Multilingual.HI-IN.Diego", "Magpie-Multilingual.HI-IN.Pascal")
    var voice by remember { mutableStateOf(if (existingPersona?.voice.isNullOrBlank()) "Magpie-Multilingual.HI-IN.Jason" else com.example.util.VoiceEmotions.getBaseVoice(existingPersona?.voice ?: "Magpie-Multilingual.HI-IN.Jason")) }'''

if search in content:
    content = content.replace(search, replacement)
else:
    print("search not found")

with open(path, "w") as f:
    f.write(content)
