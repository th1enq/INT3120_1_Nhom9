package com.example.coupleapp.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.coupleapp.ui.screens.HomeScreen
import com.example.coupleapp.ui.screens.PhoneLoginScreen
import com.example.coupleapp.ui.screens.RegisterScreen
import com.example.coupleapp.ui.screens.WelcomeScreen
import com.example.coupleapp.ui.screens.SleepTrackerScreen
import com.example.coupleapp.ui.screens.SleepCalendarHistoryScreen
import com.example.coupleapp.ui.screens.MissingScreen

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Welcome.route
    ) {
        composable(
            route = Screen.Welcome.route,
            enterTransition = {
                fadeIn(animationSpec = tween(300))
            },
            exitTransition = {
                fadeOut(animationSpec = tween(300))
            }
        ) {
            WelcomeScreen(
                onGoogleLoginClick = {
                    // Handle Google login
                    // TODO: Implement Google Sign-In
                },
                onPhoneLoginClick = {
                    navController.navigate(Screen.PhoneLogin.route) {
                        launchSingleTop = true
                    }
                },
                onSignUpClick = {
                    navController.navigate(Screen.Register.route) {
                        launchSingleTop = true
                    }
                }
            )
        }
        
        composable(
            route = Screen.PhoneLogin.route,
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(400)
                ) + fadeIn(animationSpec = tween(400))
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(400)
                ) + fadeOut(animationSpec = tween(400))
            },
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = tween(400)
                ) + fadeIn(animationSpec = tween(400))
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(400)
                ) + fadeOut(animationSpec = tween(400))
            }
        ) {
            PhoneLoginScreen(
                onLoginClick = { phone, password ->
                    // Handle phone login
                    // TODO: Implement authentication
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                },
                onBackClick = {
                    navController.popBackStack()
                },
                onForgotPasswordClick = {
                    // Handle forgot password
                    // TODO: Implement forgot password flow
                }
            )
        }
        
        composable(
            route = Screen.Register.route,
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(400)
                ) + fadeIn(animationSpec = tween(400))
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(400)
                ) + fadeOut(animationSpec = tween(400))
            },
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = tween(400)
                ) + fadeIn(animationSpec = tween(400))
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(400)
                ) + fadeOut(animationSpec = tween(400))
            }
        ) {
            RegisterScreen(
                onRegisterClick = { fullName, dateOfBirth, phone, password, confirmPassword ->
                    // Handle registration
                    // TODO: Implement registration
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                },
                onBackClick = {
                    navController.popBackStack()
                },
                onLoginClick = {
                    navController.popBackStack()
                    navController.navigate(Screen.PhoneLogin.route) {
                        launchSingleTop = true
                    }
                }
            )
        }
        
        composable(
            route = Screen.Home.route,
            enterTransition = {
                fadeIn(animationSpec = tween(600))
            }
        ) {
            HomeScreen(
                onNavigateToFeature = { featureName ->
                    // Handle feature navigation
                    // TODO: Navigate to specific features
                },
                onNavigateToWidget = { widgetName ->
                    when (widgetName) {
                        "Sleep" -> navController.navigate(Screen.SleepTracker.route) {
                            launchSingleTop = true
                        }
                        "Locket" -> navController.navigate(Screen.Locket.route) {
                            launchSingleTop = true
                        }
                        "Missing" -> navController.navigate(Screen.Missing.route) {
                            launchSingleTop = true
                        }
                    }
                }
            )
        }
        
        composable(
            route = Screen.SleepTracker.route,
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(250)
                ) + fadeIn(animationSpec = tween(250))
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(250)
                ) + fadeOut(animationSpec = tween(250))
            },
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = tween(250)
                ) + fadeIn(animationSpec = tween(250))
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(250)
                ) + fadeOut(animationSpec = tween(250))
            }
        ) {
            SleepTrackerScreen(
                onBackClick = {
                    navController.popBackStack()
                },
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNavigateToHistory = { userId ->
                    navController.navigate(Screen.SleepHistory.createRoute(userId)) {
                        launchSingleTop = true
                    }
                }
            )
        }
        
        composable(
            route = Screen.SleepHistory.route,
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(250)
                ) + fadeIn(animationSpec = tween(250))
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(250)
                ) + fadeOut(animationSpec = tween(250))
            },
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = tween(250)
                ) + fadeIn(animationSpec = tween(250))
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(250)
                ) + fadeOut(animationSpec = tween(250))
            }
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            SleepCalendarHistoryScreen(
                userId = userId,
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(
            route = Screen.WhenToSleep.route,
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(400)
                ) + fadeIn(animationSpec = tween(400))
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(400)
                ) + fadeOut(animationSpec = tween(400))
            },
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = tween(400)
                ) + fadeIn(animationSpec = tween(400))
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(400)
                ) + fadeOut(animationSpec = tween(400))
            }
        ) {
            com.example.coupleapp.ui.screens.sleep.WhenToSleepScreen(
                currentBedTime = java.time.LocalTime.of(22, 0),
                onBackClick = {
                    navController.popBackStack()
                },
                onSave = { newTime ->
                    // TODO: Save to repository
                }
            )
        }
        
        composable(
            route = Screen.SleepGoal.route,
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(400)
                ) + fadeIn(animationSpec = tween(400))
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(400)
                ) + fadeOut(animationSpec = tween(400))
            },
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = tween(400)
                ) + fadeIn(animationSpec = tween(400))
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(400)
                ) + fadeOut(animationSpec = tween(400))
            }
        ) {
            com.example.coupleapp.ui.screens.sleep.SleepGoalScreen(
                currentGoalMinutes = 480,
                onBackClick = {
                    navController.popBackStack()
                },
                onSave = { newGoal ->
                    // TODO: Save to repository
                }
            )
        }
        
        // Locket Screen
        composable(
            route = Screen.Locket.route,
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(250)
                ) + fadeIn(animationSpec = tween(250))
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(250)
                ) + fadeOut(animationSpec = tween(250))
            },
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = tween(250)
                ) + fadeIn(animationSpec = tween(250))
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(250)
                ) + fadeOut(animationSpec = tween(250))
            }
        ) {
            com.example.coupleapp.ui.screens.locket.LocketScreen(
                onBackClick = {
                    navController.popBackStack()
                },
                onNavigateToHistory = {
                    navController.navigate(Screen.LocketHistory.route) {
                        launchSingleTop = true
                    }
                },
                onNavigateToDrawing = {
                    navController.navigate(Screen.LocketDrawing.route) {
                        launchSingleTop = true
                    }
                }
            )
        }
        
        // Locket History Screen
        composable(
            route = Screen.LocketHistory.route,
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(250)
                ) + fadeIn(animationSpec = tween(250))
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(250)
                ) + fadeOut(animationSpec = tween(250))
            },
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = tween(250)
                ) + fadeIn(animationSpec = tween(250))
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(250)
                ) + fadeOut(animationSpec = tween(250))
            }
        ) {
            com.example.coupleapp.ui.screens.locket.LocketHistoryScreen(
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
        
        // Locket Drawing Screen
        composable(
            route = Screen.LocketDrawing.route,
            enterTransition = {
                slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(300))
            },
            exitTransition = {
                slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(300))
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(300))
            },
            popExitTransition = {
                slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(300))
            }
        ) {
            com.example.coupleapp.ui.screens.locket.LocketDrawingScreen(
                onBackClick = {
                    navController.popBackStack()
                },
                onSaveDrawing = { paths ->
                    // TODO: Save drawing and go back
                    navController.popBackStack()
                }
            )
        }
        
        // Missing Screen
        composable(
            route = Screen.Missing.route,
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(250)
                ) + fadeIn(animationSpec = tween(250))
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(250)
                ) + fadeOut(animationSpec = tween(250))
            },
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = tween(250)
                ) + fadeIn(animationSpec = tween(250))
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(250)
                ) + fadeOut(animationSpec = tween(250))
            }
        ) {
            MissingScreen(
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}
