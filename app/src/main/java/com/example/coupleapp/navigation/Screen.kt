package com.example.coupleapp.navigation

sealed class Screen(val route: String) {
    object Welcome : Screen("welcome")
    object PhoneLogin : Screen("phone_login")
    object Register : Screen("register")
    object Home : Screen("home")
    object SleepTracker : Screen("sleep_tracker")
    object SleepHistory : Screen("sleep_history/{userId}") {
        fun createRoute(userId: String) = "sleep_history/$userId"
    }
    object WhenToSleep : Screen("when_to_sleep")
    object SleepGoal : Screen("sleep_goal")
    
    // Locket screens
    object Locket : Screen("locket")
    object LocketHistory : Screen("locket_history")
    object LocketDrawing : Screen("locket_drawing")
}
