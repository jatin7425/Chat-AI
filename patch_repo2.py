import sys

path = "app/src/main/java/com/example/data/repository/SoulRepository.kt"
with open(path, "r") as f:
    lines = f.readlines()

new_lines = []
skip = 0
for i, line in enumerate(lines):
    if skip > 0:
        skip -= 1
        continue
    if "val emotionsStr = com.example.util.VoiceEmotions.getSupportedEmotions(" in line and "CRITICAL: Begin your response with" in lines[i+1]:
        skip = 1
        continue
    
    if "val emotionsStr = com.example.util.VoiceEmotions.getSupportedEmotions(" in line and "CRITICAL: Begin your response with" in lines[i+1]:
        skip = 1
        continue

    new_lines.append(line)

with open(path, "w") as f:
    f.writelines(new_lines)

