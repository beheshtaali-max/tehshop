package com.example.fulluikotlin.ui.screens.questions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import pw.fullvpn.android.R
import com.example.fulluikotlin.ui.theme.FullKotlinTheme
import com.example.fulluikotlin.ui.theme.fullColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionsBottomSheet(
    onDismiss: () -> Unit
) {

    val questionsViewModel: QuestionsViewModel = koinViewModel()
    val uiState by questionsViewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.fullColors.bgBottomSheet,
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(90.dp)
                    .height(8.dp)
                    .background(
                        color = MaterialTheme.fullColors.dragHandleBottomSheet,
                        shape = RoundedCornerShape(50)
                    )
            )
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .background(MaterialTheme.fullColors.bgBottomSheet)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "سوالات متداول",
                    color = MaterialTheme.fullColors.textTitleDevicesActive,
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily(Font(R.font.yekanbakh_bold)),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // لیست سوالات
                LazyColumn(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(
                        items = uiState.items,
                        key = { _, item -> item.faq.question }
                    ) { index, item ->
                        ItemsQuestions(
                            item = item,
                            onClick = { questionsViewModel.toggleExpanded(index) }
                        )
                        if (index < uiState.items.lastIndex) {
                            Divider(
                                color = MaterialTheme.fullColors.GrayDark7,
                                thickness = 0.5.dp
                            )
                        }
                    }

                    if (uiState.items.isEmpty()) {
                        item {
                            Text(
                                text = "هیچ سوالی یافت نشد",
                                modifier = Modifier.padding(32.dp),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.fullColors.GrayDark7
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.fullColors.purple
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "متوجه شدم",
                        modifier = Modifier.padding(vertical = 5.dp),
                        color = MaterialTheme.fullColors.whit,
                        fontFamily = FontFamily(Font(R.font.yekanbakh_bold))
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun ItemsQuestions(
    item: QuestionsItemState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = item.faq.question,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.fullColors.textTitleDevicesActive,
                fontFamily = FontFamily(Font(R.font.yekanbakh_bold)),
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Icon(
                painter = painterResource(
                    if (item.isExpanded) R.drawable.ic_arrow_circle_left   // down arrow
                    else R.drawable.ic_arrow_circle_down                    // left arrow
                ),
                contentDescription = if (item.isExpanded) "بستن پاسخ" else "باز کردن پاسخ",
                tint = MaterialTheme.fullColors.GrayDark7,
            )
        }

        if (item.isExpanded) {
            Spacer(
                modifier = Modifier
                    .height(8.dp)
                    .width(10.dp)
            )
            Text(
                text = item.faq.answer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.fullColors.textLastActiveDevicesActive,
                fontFamily = FontFamily(Font(R.font.yekanbakh_regular)),
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(
    showBackground = true,
    showSystemUi = true
)
@Composable
fun QuestionsBottomSheetPreview() {

    FullKotlinTheme(true) {

        var showSheet by remember { mutableStateOf(true) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF101010))
        ) {

            if (showSheet) {
                QuestionsBottomSheet(
                    onDismiss = { showSheet = false }
                )
            }
        }
    }
}
