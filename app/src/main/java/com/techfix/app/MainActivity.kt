package com.techfix.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import com.techfix.app.R
import com.techfix.app.core.designsystem.FixoraTheme
import com.techfix.app.core.navigation.FixoraNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FixoraRoot()
        }
    }
}

@Composable
private fun FixoraRoot() {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    var darkTheme by remember {
        mutableStateOf(
            context.getSharedPreferences("fixora_preferences", 0)
                .getBoolean("dark_theme", systemDark),
        )
    }
    var showSplash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(280)
        showSplash = false
    }
    FixoraTheme(darkTheme = darkTheme) {
        // background, not the Surface default. Surface is the card/sheet
        // token; every Scaffold screen already sets background as its
        // container, and the auth screens have no Scaffold, so leaving the
        // default here put them on a different ground to the rest of the app.
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Crossfade(targetState = showSplash, animationSpec = tween(200), label = "splashToApp") { splash ->
                if (splash) {
                    SplashScreen()
                } else {
                    FixoraNavHost(
                        darkTheme = darkTheme,
                        onThemeChange = { enabled ->
                            darkTheme = enabled
                            context.getSharedPreferences("fixora_preferences", 0)
                                .edit()
                                .putBoolean("dark_theme", enabled)
                                .apply()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SplashScreen() {
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Image(
            painter = painterResource(R.drawable.ic_fixora_logo),
            contentDescription = "Fixora",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(112.dp),
        )
    }
}
