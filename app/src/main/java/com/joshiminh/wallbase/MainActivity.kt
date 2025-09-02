package com.joshiminh.wallbase

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.joshiminh.wallbase.ui.explore.ExploreScreen
import com.joshiminh.wallbase.ui.theme.WallBaseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WallBaseTheme {
                val sources = remember {
                    listOf(
                        SourceOption("Source 1", mutableStateOf(true)),
                        SourceOption("Source 2", mutableStateOf(true))
                    )
                }
                Surface {
                    ExploreScreen(sources)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ExplorePreview() {
    val sources = listOf(
        SourceOption("Source 1", mutableStateOf(true)),
        SourceOption("Source 2", mutableStateOf(true))
    )
    WallBaseTheme {
        Surface {
            ExploreScreen(sources)
        }
    }
}
