package app.folga.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeHeader(
    userName: String,
    userTeam: String,
    userPhotoUrl: String?,
    pendingSwapsCount: Int,
    onOpenProfile: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenReports: () -> Unit,
) {
    val primary = Color(0xFF1E3A8A)
    Surface(
        color = primary,
        shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 64.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Bloodtype,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = userTeam.ifBlank { "Hemato" },
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onOpenReports() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Filled.Assessment, "Relatórios", tint = Color.White)
                    }
                    IconButton(
                        onClick = { onOpenNotifications() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        BadgedBox(
                            badge = { if (pendingSwapsCount > 0) Badge { Text(pendingSwapsCount.toString()) } }
                        ) {
                            Icon(Icons.Filled.Notifications, "Notificações", tint = Color.White)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(modifier = Modifier.size(40.dp).clickable { onOpenProfile() }) {
                        ProfileAvatar(name = userName, photoUrl = userPhotoUrl, size = 40.dp)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = buildAnnotatedString {
                    append("Olá, ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(userName.firstName())
                    }
                },
                color = Color.White,
                fontSize = 32.sp
            )
        }
    }
}

private fun String.firstName(): String = trim().substringBefore(' ')
