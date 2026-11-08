package com.tushar.voidplayer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tushar.voidplayer.ui.theme.*
import com.tushar.voidplayer.ui.theme.PrimaryText
import com.tushar.voidplayer.ui.theme.SecondaryText
import com.tushar.voidplayer.ui.theme.SurfaceElevated

data class NavItem(
    val title: String,
    val icon: ImageVector,
    val badge: String? = null
)

@Composable
fun BottomNavBar(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    accentColor: Color,
    hasActiveSong: Boolean,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        NavItem("Library", Icons.Filled.MusicNote),
        NavItem("AI Hub", Icons.Filled.AutoAwesome, "AI"),
        NavItem("Playlists", Icons.AutoMirrored.Filled.PlaylistPlay),
        NavItem("Playing", Icons.Filled.Album, if (hasActiveSong) "●" else null)
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(if (VoidTheme.isFlat) 0.dp else 20.dp, RoundedCornerShape(28.dp), spotColor = Color.Black.copy(alpha = 0.6f))
            .clip(RoundedCornerShape(28.dp)),
        color = if (VoidTheme.isFlat) SurfacePane else SurfaceElevated,
        shape = RoundedCornerShape(28.dp),
        border = if (VoidTheme.isFlat) androidx.compose.foundation.BorderStroke(1.dp, HairlineColor) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = selectedTab == index
                val scale by animateFloatAsState(if (isSelected) 1.08f else 1.0f)
                // Graphite marks the selected tab with a neutral thumb; Classic tints it with the accent.
                val flat = VoidTheme.isFlat
                val iconTint by animateColorAsState(if (isSelected) (if (flat) PrimaryText else accentColor) else SecondaryText)
                val textTint by animateColorAsState(if (isSelected) PrimaryText else SecondaryText)
                val selectedBackground = if (flat) HairlineColor else accentColor.copy(alpha = 0.15f)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) selectedBackground else Color.Transparent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelectTab(index) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.scale(scale)
                    ) {
                        Box(contentAlignment = Alignment.TopEnd) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.title,
                                tint = iconTint,
                                modifier = Modifier.size(24.dp)
                            )
                            if (item.badge != null && !isSelected) {
                                Box(
                                    modifier = Modifier
                                        .offset(x = 6.dp, y = (-4).dp)
                                        .size(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(accentColor)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = item.title,
                            color = textTint,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}
