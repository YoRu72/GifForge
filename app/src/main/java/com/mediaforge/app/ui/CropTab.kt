package com.mediaforge.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mediaforge.app.media.VideoInfo

@Composable
fun CropTab(state: EditorState, info: VideoInfo?) {
    if (info == null) {
        Text("Loading video...", Modifier.padding(16.dp))
        return
    }
    CropControls(state.crop, info.width, info.height)
}
