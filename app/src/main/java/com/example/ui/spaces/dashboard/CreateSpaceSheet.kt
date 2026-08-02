package com.example.ui.spaces.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.customTextFieldColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSpaceSheet(
    onDismiss: () -> Unit,
    onCreate: (name: String, premise: String) -> Unit,
    isSaving: Boolean,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf("") }
    var premise by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "New Space",
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Give your story a name and, if you like, a premise.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                placeholder = { Text("e.g. Startup Life") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = customTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = premise,
                onValueChange = { premise = it },
                label = { Text("Premise (optional)") },
                placeholder = { Text("What story is this?") },
                shape = RoundedCornerShape(16.dp),
                colors = customTextFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp)
            )

            Button(
                onClick = { onCreate(name.trim(), premise.trim()) },
                enabled = name.isNotBlank() && !isSaving,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                } else {
                    Text("Create Space", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
