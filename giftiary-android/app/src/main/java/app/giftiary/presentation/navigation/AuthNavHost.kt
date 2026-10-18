package app.giftiary.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.giftiary.presentation.auth.CheckEmailScreen
import app.giftiary.presentation.auth.CreateAccountScreen
import app.giftiary.presentation.auth.ForgotPasswordScreen
import app.giftiary.presentation.auth.ForgotPasswordViewModel
import app.giftiary.presentation.auth.SignInScreen
import app.giftiary.presentation.sharing.PendingJoinViewModel
import kotlinx.serialization.Serializable

@Serializable
data object SignInRoute

@Serializable
data object CreateAccountRoute

@Serializable
data class CheckEmailRoute(val email: String)

/** [email] only pre-fills the form, as typed on the sign-in screen. */
@Serializable
data class ForgotPasswordRoute(val email: String = "")

/** The signed-out side of the app. Signing in leaves it through the auth gate, not by navigating. */
@Composable
fun AuthNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val joinPending by hiltViewModel<PendingJoinViewModel>().isPending.collectAsStateWithLifecycle()
    NavHost(
        navController = navController,
        startDestination = SignInRoute,
        modifier = modifier,
    ) {
        composable<SignInRoute> {
            SignInScreen(
                viewModel = hiltViewModel(),
                joinPending = joinPending,
                onCreateAccount = dropUnlessResumed { navController.navigate(CreateAccountRoute) },
                onForgotPassword = { email -> navController.navigate(ForgotPasswordRoute(email.trim())) { launchSingleTop = true } },
            )
        }
        composable<ForgotPasswordRoute> { entry ->
            val email = entry.toRoute<ForgotPasswordRoute>().email
            ForgotPasswordScreen(
                viewModel = hiltViewModel<ForgotPasswordViewModel, ForgotPasswordViewModel.Factory> { factory -> factory.create(email) },
                onBack = dropUnlessResumed { navController.popBackStack() },
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
