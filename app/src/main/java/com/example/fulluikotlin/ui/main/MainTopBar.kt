package com.example.fulluikotlin.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pw.fullvpn.android.R
import pw.fullvpn.android.BuildConfig
import com.example.fulluikotlin.ui.theme.FullKotlinTheme
import com.example.fulluikotlin.ui.theme.fullColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTopBar(
    onNotificationClick: () -> Unit,
    onQuestionsClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp)
            .fillMaxWidth()
            .height(80.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {

        Card(
            modifier = Modifier.size(45.dp),
            shape = RoundedCornerShape(8.dp),
            onClick = {
                onQuestionsClick()
            },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.fullColors.bgToolbarButton),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_message),
                contentDescription = "LeftIconToolbar",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                tint = MaterialTheme.fullColors.iconColor
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = buildAnnotatedString {
                    withStyle(
                        style = SpanStyle(color = MaterialTheme.fullColors.purple)
                    ) {
                        append("Teh ")
                    }
                    withStyle(
                        style = SpanStyle(color = MaterialTheme.fullColors.whitBlack)
                    ) {
                        append("VPN")
                    }
                },
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily(Font(R.font.poppins_bold))
            )

            Text(
                text = "version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.fullColors.whitBlack.copy(alpha = 0.65f),
                textAlign = TextAlign.Center,
                fontFamily = FontFamily(Font(R.font.poppins_medium))
            )
        }

        Card(
            modifier = Modifier
                .size(45.dp)
                .clickable { onNotificationClick() },
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.fullColors.bgToolbarButton)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_notification),
                contentDescription = "LeftIconToolbar",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                tint = MaterialTheme.fullColors.iconColor
            )
        }

    }
}

@Preview(showBackground = true)
@Composable
fun MainTopBarPreview() {
    FullKotlinTheme {
        MainTopBar(
            onNotificationClick = {},
            onQuestionsClick = {}
        )
    }
}

