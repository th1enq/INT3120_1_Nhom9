package com.example.coupleapp.navigation

sealed class Screen(val route: String) {
    object Welcome : Screen("welcome")
    object PhoneLogin : Screen("phone_login")
    object Register : Screen("register")
    object Home : Screen("home")
}
