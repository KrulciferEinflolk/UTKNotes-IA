// Controles del mini reproductor superpuestos
Box(
    modifier = Modifier
        .fillMaxSize()
        .background(
            androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.6f),
                    Color.Transparent,
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.6f)
                )
            )
        )
) {
    // Title at the top center
    Text(
        text = block.title,
        color = Color.White,
        fontSize = 14.sp,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 16.dp)
    )

    // Play/Pause capturing area
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { isPlaying = !isPlaying }
    )

    // Big play button in the center if paused
    if (!isPlaying) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.3f))
                .clickable { isPlaying = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
        }
    }

    // Bottom controls
    Row(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = String.format("%02d:%02d", currentPos / 1000 / 60, (currentPos / 1000) % 60),
            color = Color.White,
            fontSize = 12.sp
        )
        Slider(
            value = progress,
            onValueChange = {
                progress = it
                val newPos = (it * duration).toInt()
                videoView?.seekTo(newPos)
                currentPos = newPos
            },
            modifier = Modifier
                .weight(1f)
                .height(24.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
            )
        )
        Text(
            text = String.format("%02d:%02d", duration / 1000 / 60, (duration / 1000) % 60),
            color = Color.White,
            fontSize = 12.sp
        )
        IconButton(
            onClick = {
                try {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                    intent.setDataAndType(android.net.Uri.parse(block.sourceUrl), "video/*")
                    intent.flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "No se puede reproducir", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                Icons.Default.Fullscreen, // Ideally open_in_full but fullscreen is fine
                contentDescription = "Maximizar",
                tint = Color.White
            )
        }
    }
}
