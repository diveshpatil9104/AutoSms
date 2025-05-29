package com.example.autowish.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.autowish.ui.screens.AddBirthdayScreen
import com.example.autowish.ui.screens.HomeScreen
import com.example.autowish.viewmodel.BirthdayViewModel

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val viewModel: BirthdayViewModel = viewModel()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onAddClicked = {
                    navController.navigate("add_birthday")
                }
            )
        }
        composable("add_birthday") {
            AddBirthdayScreen(
                viewModel = viewModel,
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}