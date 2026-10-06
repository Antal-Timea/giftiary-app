package app.gifty.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.gifty.presentation.auth.CheckEmailScreen
import app.gifty.presentation.auth.CreateAccountScreen
import app.gifty.presentation.auth.SignInScreen
import kotlinx.serialization.Serializable

@Serializable
data object SignInRoute

@Serializable
data object CreateAccountRoute

@Serializable
data class CheckEmailRoute(val email: String)

/** The signed-out side of the app. Signing in leaves it through the auth gate, not by navigating. */
@Composable
fun AuthNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = SignInRoute,
        modifier = modifier,
    ) {
        composable<SignInRoute> {
            SignInScreen(
                viewModel = hiltViewModel(),
                onCreateAccount = dropUnlessResumed { navController.navigate(CreateAccountRoute) },
            )
        }
        composable<CreateAccountRoute> {
            CreateAccountScreen(
                viewModel = hiltViewModel(),
                onBack = dropUnlessResumed { navController.popBackStack() },
                onConfirmationSent = { email ->
                    // The account form is done; going back from here returns to sign-in.
                    navController.navigate(CheckEmailRoute(email)) {
                        popUpTo(SignInRoute)
                    }
                },
            )
        }
        composable<CheckEmailRoute> { entry ->
            CheckEmailScreen(
                email = entry.toRoute<CheckEmailRoute>().email,
                onBackToSignIn = dropUnlessResumed { navController.popBackStack(SignInRoute, inclusive = false) },
            )
        }
    }
}
