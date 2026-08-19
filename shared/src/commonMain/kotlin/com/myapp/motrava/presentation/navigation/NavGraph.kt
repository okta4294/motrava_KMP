package com.myapp.motrava.presentation.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.myapp.motrava.presentation.auth.LoginScreen
import com.myapp.motrava.presentation.splash.SplashScreen
import com.myapp.motrava.presentation.auth.RegisterScreen
import com.myapp.motrava.presentation.notification.NotificationScreen
import com.myapp.motrava.presentation.service.ServiceScreen
import com.myapp.motrava.presentation.dashboard.DashboardScreen
import com.myapp.motrava.presentation.profile.ProfileScreen
import com.myapp.motrava.presentation.service.AddServiceScreen
import com.myapp.motrava.presentation.tracking.TrackingScreen
import com.myapp.motrava.presentation.trip.TripDetailScreen
import com.myapp.motrava.presentation.vehicle.AddVehicleScreen
import com.myapp.motrava.presentation.theme.*

sealed class Screen(val route: String, val title: String, val icon: ImageVector? = null) {
    object Splash : Screen("splash", "Splash")
    object Login : Screen("login", "Login")
    object Register : Screen("register", "Register")
    object Notifications : Screen("notifications", "Notifications")
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Dashboard)
    object Tracking : Screen("tracking", "Tracking", Icons.Default.Map)
    object Service : Screen("service", "Service", Icons.Default.Build)
    object Profile : Screen("profile", "Profile", Icons.Default.Person)
    object AddVehicle : Screen("add_vehicle", "Add Vehicle")
    object EditVehicle : Screen("edit_vehicle/{vehicleId}", "Edit Vehicle") {
        fun createRoute(vehicleId: String) = "edit_vehicle/$vehicleId"
    }
    object AddService : Screen("add_service", "Add Service")
    object TripDetail : Screen("trip_detail/{tripId}", "Trip Detail") {
        fun createRoute(tripId: String) = "trip_detail/$tripId"
    }
}

val bottomNavItems = listOf(
    Screen.Dashboard,
    Screen.Tracking,
    Screen.Service,
    Screen.Profile
)

// navItemColor removed, we now use a single color for active items

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MotravaApp(isDarkMode: Boolean, onThemeToggle: () -> Unit) {
    val tokenManager: com.myapp.motrava.data.local.TokenManager = org.koin.compose.koinInject()
    val hasToken = tokenManager.accessToken != null

    val navController = rememberNavController()
    var isLoggedIn by rememberSaveable { mutableStateOf(hasToken) }
    val startDest = Screen.Splash.route

    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    val showShell = currentRoute != Screen.Splash.route &&
            currentRoute != Screen.Login.route &&
            currentRoute != Screen.Register.route &&
            isLoggedIn

    LaunchedEffect(Unit) {
        tokenManager.loggedOutEvent.collect {
            isLoggedIn = false
            navController.navigate(Screen.Login.route) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AnimatedVisibility(
                visible = showShell,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                TopAppBar(
                    title = { 
                        Text(
                            text = "Motrava", 
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        ) 
                    },
                    actions = {
                        IconButton(onClick = onThemeToggle) {
                            Icon(
                                imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Toggle Theme",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { navController.navigate(Screen.Notifications.route) }) {
                            Icon(
                                imageVector = Icons.Outlined.Notifications,
                                contentDescription = "Notifications",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isLoggedIn) {
                            IconButton(onClick = {
                                isLoggedIn = false
                                tokenManager.clearTokens()
                                navController.navigate(Screen.Login.route) {
                                    popUpTo(navController.graph.id) { inclusive = true }
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Logout,
                                    contentDescription = "Logout",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = showShell,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        val isSelected = currentRoute == screen.route
                        NavigationBarItem(
                            icon = { Icon(screen.icon!!, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = isSelected,
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDest,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Splash.route) {
                SplashScreen(
                    onSplashFinished = { isLoggedInUser ->
                        isLoggedIn = isLoggedInUser
                        val targetRoute = if (isLoggedInUser) Screen.Dashboard.route else Screen.Login.route
                        navController.navigate(targetRoute) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Login.route) {
                LoginScreen(
                    onLoginSuccess = {
                        isLoggedIn = true
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    },
                    onNavigateToRegister = {
                        navController.navigate(Screen.Register.route)
                    }
                )
            }
            composable(Screen.Register.route) {
                RegisterScreen(
                    onRegisterSuccess = {
                        // In a real app, maybe log them in automatically
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Register.route) { inclusive = true }
                        }
                    },
                    onNavigateToLogin = {
                        navController.popBackStack()
                    }
                )
            }
            composable(Screen.Dashboard.route) { 
                DashboardScreen(
                    onNavigateToTripDetail = { tripId -> navController.navigate(Screen.TripDetail.createRoute(tripId)) }
                ) 
            }
            composable(Screen.Tracking.route) { 
                TrackingScreen(
                    onNavigateToAddVehicle = { navController.navigate(Screen.AddVehicle.route) }
                ) 
            }
            composable(Screen.Notifications.route) {
                NotificationScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Service.route) { ServiceScreen() }
            composable(Screen.Profile.route) { 
                ProfileScreen(
                    onNavigateToAddVehicle = { navController.navigate(Screen.AddVehicle.route) },
                    onNavigateToEditVehicle = { id -> navController.navigate(Screen.EditVehicle.createRoute(id)) }
                ) 
            }
            composable(Screen.AddVehicle.route) { 
                AddVehicleScreen(onVehicleAdded = { navController.popBackStack() }) 
            }
            composable(
                route = Screen.EditVehicle.route,
                arguments = listOf(androidx.navigation.navArgument("vehicleId") { type = androidx.navigation.NavType.StringType })
            ) { backStackEntry ->
                val vehicleId = backStackEntry.arguments?.getString("vehicleId")
                AddVehicleScreen(
                    vehicleId = vehicleId,
                    onVehicleAdded = { navController.popBackStack() }
                )
            }
            composable(Screen.AddService.route) { 
                AddServiceScreen(onServiceSaved = { navController.popBackStack() }) 
            }
            composable(
                route = Screen.TripDetail.route,
                arguments = listOf(androidx.navigation.navArgument("tripId") { type = androidx.navigation.NavType.StringType })
            ) { backStackEntry ->
                val tripId = backStackEntry.arguments?.getString("tripId") ?: return@composable
                TripDetailScreen(
                    tripId = tripId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}

