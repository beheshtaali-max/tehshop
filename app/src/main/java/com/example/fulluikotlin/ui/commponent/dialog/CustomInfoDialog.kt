package com.example.fulluikotlin.ui.commponent.dialog


import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pw.fullvpn.android.R
import com.example.fulluikotlin.domain.model.CustomDialogStatus
import com.example.fulluikotlin.ui.theme.FullKotlinTheme
import com.example.fulluikotlin.ui.theme.fullColors

@Composable
fun CustomInfoDialog(
    model: CustomDialogStatus,
    onButtonClick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        ),
        onDismissRequest = onDismiss
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.fullColors.bgBottomSheet
                )
            ) {
                Column(
                    modifier = Modifier
                        .background(MaterialTheme.fullColors.bgBottomSheet)
                        .padding(20.dp)
                ) {

                    Card(
                        modifier = Modifier
                            .padding(vertical = 5.dp)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.fullColors.bgDiscExitDevice
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 15.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = model.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.fullColors.textTitleDevicesActive,
                                textAlign = TextAlign.End,
                                fontFamily = FontFamily(Font(R.font.yekanbakh_bold))
                            )
                        }
                    }

                    Text(
                        text = model.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.fullColors.whitBlack,
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily(Font(R.font.yekanbakh_bold)),
                        modifier = Modifier
                            .padding(top = 20.dp, bottom = 25.dp)
                            .fillMaxWidth()
                    )


                    Button(
                        onClick = { onButtonClick(model.buttonLink) },
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally),
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.fullColors.buttonAcceptDialog
                        ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.fullColors.buttonAcceptDialog
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = model.buttonText,
                            modifier = Modifier.padding(vertical = 2.dp, horizontal = 10.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.fullColors.textAcceptDialog,
                            textAlign = TextAlign.End,
                            fontFamily = FontFamily(Font(R.font.yekanbakh_bold))
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CustomInfoDialogPreview() {
    FullKotlinTheme(true) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFE0E0E0))
        ) {
            CustomInfoDialog(
                model = CustomDialogStatus(
                    status = "SUCSESSCULL",
                    id = "12",
                    title = "اطلاعیه فوری",
                    description = "متن اطلاعیه",
                    buttonText = "متوجه شدم",
                    buttonLink = "https://go.com",
                    time = "",
                    startTime = 1777974668L,
                    useSteps = 2,
                    forceShow = "always"
                ),
                onDismiss = {},
                onButtonClick = {}
            )
        }
    }
}
