package com.driss.puzzles

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.driss.puzzles.ui.crop.CropScreen
import com.driss.puzzles.ui.game.GameScreen
import com.driss.puzzles.ui.home.HomeScreen
import com.driss.puzzles.ui.setup.SetupScreen
import com.driss.puzzles.ui.theme.PuzzlesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PuzzlesTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
private fun AppNavigation() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onOpen = { id -> nav.navigate("setup/$id") },
                onCrop = { uri -> nav.navigate("crop?uri=${Uri.encode(uri.toString())}") },
            )
        }
        composable("crop?uri={uri}", arguments = listOf(navArgument("uri") { type = NavType.StringType })) { entry ->
            val uri = Uri.parse(entry.arguments?.getString("uri").orEmpty())
            CropScreen(
                uri = uri,
                onBack = { nav.popBackStack() },
                onSaved = { id -> nav.navigate("setup/$id") { popUpTo("home") } },
            )
        }
        composable("setup/{imageId}") { entry ->
            SetupScreen(
                imageId = entry.arguments?.getString("imageId").orEmpty(),
                onBack = { nav.popBackStack() },
                onPlay = { id, target, resume -> nav.navigate("game/$id/$target/$resume") },
            )
        }
        composable(
            "game/{imageId}/{target}/{resume}",
            arguments = listOf(
                navArgument("target") { type = NavType.IntType },
                navArgument("resume") { type = NavType.BoolType },
            ),
        ) { entry ->
            val args = entry.arguments!!
            GameScreen(
                imageId = args.getString("imageId").orEmpty(),
                target = args.getInt("target"),
                resume = args.getBoolean("resume"),
                onExit = { nav.popBackStack("home", inclusive = false) },
                onBack = { nav.popBackStack() },
            )
        }
    }
}
