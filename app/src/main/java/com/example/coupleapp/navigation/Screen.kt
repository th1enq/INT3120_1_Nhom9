package com.example.coupleapp.navigation

sealed class Screen(val route: String) {
    object Welcome : Screen("welcome")
    object PhoneLogin : Screen("phone_login")
    object Register : Screen("register")
    object Home : Screen("home")
    
    // Partner screens
    object PartnerHub : Screen("partner_hub")
    object LinkPartner : Screen("link_partner")
    object Chat : Screen("chat")
    object QA : Screen("qa")
    
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
    
    // Missing screen
    object Missing : Screen("missing")
    
    // Distance screens
    object Distance : Screen("distance?targetPlaceId={targetPlaceId}") {
        fun createRoute(targetPlaceId: String? = null): String {
            return if (targetPlaceId != null) {
                "distance?targetPlaceId=$targetPlaceId"
            } else {
                "distance"
            }
        }
    }
    object SharedPlaces : Screen("shared_places")
    object PlacePhotos : Screen("place_photos/{placeId}") {
        fun createRoute(placeId: String) = "place_photos/$placeId"
    }
    
    // Store screen
    object Store : Screen("store")
    
    // Calendar screen
    object Calendar : Screen("calendar")
    
    // Quest screen
    object Quest : Screen("quest")
    
    // Garden screen
    object Garden : Screen("garden")
    object GardenGallery : Screen("garden_gallery")

    // Moments screen
    object Moments : Screen("moments")
    
    // Profile screen
    object Profile : Screen("profile")
    object EditProfile : Screen("edit_profile")
    object ChangePassword : Screen("change_password")
    object ManageLink : Screen("manage_link")
    object NotificationSettings : Screen("notification_settings")
    object LanguageSettings : Screen("language_settings")
    object PermissionsSettings : Screen("permissions_settings")
    object ImportantPlaces : Screen("important_places")
    object Help : Screen("help")
    object About : Screen("about")
}