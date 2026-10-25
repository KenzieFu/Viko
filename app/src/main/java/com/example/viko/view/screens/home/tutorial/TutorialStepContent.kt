package com.example.viko.view.screens.home.tutorial

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viko.view.theme.VSurface
import com.example.viko.view.theme.VikoTheme

/** One layout for all five spotlights, including the step inside the team sheet. */
@Composable
internal fun TutorialStepContent(
    step: TutorialStep,
    onNext: () -> Unit,
    onSkipAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Keep actions reachable when large text needs the copy to scroll.
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            Text("${step.stepNumber} / ${TutorialStep.total}", fontSize = 12.sp, lineHeight = 16.sp, color = Color.White)
            Spacer(Modifier.height(16.dp))
            Text(step.title, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text(step.body, fontSize = 14.sp, lineHeight = 20.sp, color = Color.White.copy(alpha = .8f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onSkipAll, contentPadding = PaddingValues(vertical = 12.dp),
                modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Skip All", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
            Button(onClick = onNext, shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VSurface, contentColor = Color.White),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (step.isLast) "Done" else "Next", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Preview(widthDp = 320, showBackground = true, backgroundColor = 0xFF0B0B0B)
@Composable
private fun TutorialStepContentPreview() { VikoTheme { TutorialStepContent(TutorialStep.Announcement, {}, {}) } }
