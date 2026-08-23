package com.techfix.app.core.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import com.techfix.app.core.data.RepositoryProvider
import com.techfix.app.core.data.auth.AuthRepositoryProvider
import com.techfix.app.domain.auth.AuthUser
import com.techfix.app.domain.matching.MatchBranchesUseCase
import com.techfix.app.ui.auth.AuthViewModel
import com.techfix.app.ui.auth.LoginScreen
import com.techfix.app.ui.auth.RegisterScreen
import com.techfix.app.domain.catalog.DeviceCategory
import com.techfix.app.BuildConfig
import com.techfix.app.ui.customer.CustomerBottomBar
import com.techfix.app.ui.customer.CustomerHomeScreen
import com.techfix.app.ui.customer.CustomerHomeViewModel
import com.techfix.app.ui.customer.CustomerTab
import com.techfix.app.ui.customer.profile.ProfileScreen
import com.techfix.app.ui.customer.profile.ProfileViewModel
import com.techfix.app.ui.customer.profile.EditProfileScreen
import com.techfix.app.ui.customer.booking.BookRepairScreen
import com.techfix.app.ui.customer.booking.BookRepairViewModel
import com.techfix.app.ui.customer.catalog.ServiceCatalogScreen
import com.techfix.app.ui.customer.catalog.ServiceCatalogViewModel
import com.techfix.app.ui.customer.catalog.ServiceDetailScreen
import com.techfix.app.ui.customer.catalog.ServiceDetailViewModel
import com.techfix.app.ui.customer.payment.PaymentScreen
import com.techfix.app.ui.customer.payment.PaymentViewModel
import com.techfix.app.ui.customer.repair.RepairHistoryDetailScreen
import com.techfix.app.ui.customer.repair.RepairHistoryDetailViewModel
import com.techfix.app.ui.customer.repair.RepairHistoryScreen
import com.techfix.app.ui.customer.repair.RepairHistoryViewModel
import com.techfix.app.ui.customer.repair.RepairTrackingScreen
import com.techfix.app.ui.customer.repair.RepairTrackingViewModel
import com.techfix.app.ui.staff.StaffAppointmentDetailScreen
import com.techfix.app.ui.staff.StaffAppointmentDetailViewModel
import com.techfix.app.ui.staff.StaffAppointmentsScreen
import com.techfix.app.ui.staff.StaffAppointmentsViewModel
import com.techfix.app.ui.staff.StaffContext
import com.techfix.app.ui.staff.StaffDashboardScreen
import com.techfix.app.ui.staff.StaffDashboardViewModel
import com.techfix.app.ui.staff.StaffInventoryScreen
import com.techfix.app.ui.staff.StaffInventoryViewModel
import com.techfix.app.ui.staff.StaffQueueTab
import com.techfix.app.ui.staff.StaffBottomBar
import com.techfix.app.ui.staff.StaffInventoryTab
import com.techfix.app.ui.staff.StaffOperationsViewModel
import com.techfix.app.ui.staff.StaffBranchesScreen
import com.techfix.app.ui.staff.StaffUsersScreen
import com.techfix.app.ui.staff.StaffReportsScreen
import com.techfix.app.ui.staff.StaffMoreScreen
import com.techfix.app.ui.staff.inventory.AdminInventoryScreen
import com.techfix.app.ui.staff.inventory.AdminInventoryViewModel

@Composable
fun FixoraNavHost(
    navController: NavHostController = rememberNavController(),
    sessionViewModel: SessionViewModel = viewModel(),
    darkTheme: Boolean = false,
    onThemeChange: (Boolean) -> Unit = {},
) {
    val role by sessionViewModel.role.collectAsState()
    val user by sessionViewModel.user.collectAsState()

    val startDestination = when {
        role == null -> Graph.AUTH
        role == UserRole.CUSTOMER -> Graph.CUSTOMER
        else -> Graph.STAFF
    }

    // The bottom bar belongs to the customer's five top-level destinations and
    // nothing else: auth, the staff screen set, and every customer drill-down
    // (booking, tracking, payment, a service or history detail) render without
    // it. Keeping it here rather than inside each screen means one bar
    // instance that survives the tab switch instead of five that animate in.
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in CustomerTab.routes
    val showStaffBottomBar = currentRoute == StaffRoutes.DASHBOARD ||
        currentRoute == StaffRoutes.INVENTORY ||
        currentRoute == StaffRoutes.TECHNICIANS ||
        currentRoute == StaffRoutes.MORE ||
        currentRoute?.startsWith("staff/queue") == true

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // Zero, because every screen inside still runs its own Scaffold and
        // handles the status bar itself. This one only contributes the height
        // of the bar; the bar applies the navigation-bar inset on its own.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                CustomerBottomBar(
                    currentRoute = currentRoute,
                    onTabSelected = { tab -> navController.navigateToTab(tab) },
                )
            } else if (showStaffBottomBar && role != null) {
                StaffBottomBar(
                    role = role!!,
                    currentRoute = currentRoute,
                    onTabSelected = { target ->
                        navController.navigate(target) {
                            popUpTo(StaffRoutes.DASHBOARD) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { scaffoldPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(scaffoldPadding),
            enterTransition = { fadeIn(tween(200)) },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(200)) },
            popExitTransition = { fadeOut(tween(200)) },
        ) {
            authGraph(navController, sessionViewModel)
            customerGraph(navController, sessionViewModel, user, darkTheme, onThemeChange)
            staffGraph(navController, sessionViewModel, user)
        }
    }
}

/**
 * Tab switching, not stacking: each tab is popped back to Home rather than
 * pushed on top of the last one, so Back from any tab leaves for Home instead
 * of walking the tabs the customer happened to visit. State is saved and
 * restored per tab, so returning to a tab keeps its scroll position and
 * filters.
 */
private fun NavHostController.navigateToTab(tab: CustomerTab) {
    // The Services tab's route carries an optional category argument; the
    // unfiltered form is what the tab opens.
    val target = if (tab == CustomerTab.SERVICES) CustomerRoutes.catalog() else tab.route
    navigate(target) {
        popUpTo(CustomerRoutes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun onSignedIn(
    navController: NavHostController,
    sessionViewModel: SessionViewModel,
    user: AuthUser,
) {
    sessionViewModel.signIn(user)
    val graph = if (user.role == UserRole.CUSTOMER) Graph.CUSTOMER else Graph.STAFF
    navController.navigate(graph) {
        popUpTo(Graph.AUTH) { inclusive = true }
    }
}

private fun NavGraphBuilder.authGraph(
    navController: NavHostController,
    sessionViewModel: SessionViewModel,
) {
    navigation(startDestination = AuthRoutes.LOGIN, route = Graph.AUTH) {
        composable(AuthRoutes.LOGIN) {
            val context = LocalContext.current
            val authViewModel: AuthViewModel = viewModel(
                factory = AuthViewModel.factory(AuthRepositoryProvider.instance),
            )
            val uiState by authViewModel.uiState.collectAsState()
            LoginScreen(
                uiState = uiState,
                onEmailChange = authViewModel::onEmailChange,
                onPasswordChange = authViewModel::onPasswordChange,
                onLoginClick = {
                    authViewModel.login { user -> onSignedIn(navController, sessionViewModel, user) }
                },
                onGoogleClick = {
                    authViewModel.loginWithGoogle(context) { user ->
                        onSignedIn(navController, sessionViewModel, user)
                    }
                },
                onNavigateToRegister = { navController.navigate(AuthRoutes.REGISTER) },
            )
        }
        composable(AuthRoutes.REGISTER) {
            val authViewModel: AuthViewModel = viewModel(
                factory = AuthViewModel.factory(AuthRepositoryProvider.instance),
            )
            val uiState by authViewModel.uiState.collectAsState()
            RegisterScreen(
                uiState = uiState,
                onEmailChange = authViewModel::onEmailChange,
                onPasswordChange = authViewModel::onPasswordChange,
                onRegisterClick = {
                    authViewModel.register { user -> onSignedIn(navController, sessionViewModel, user) }
                },
                onNavigateToLogin = { navController.popBackStack() },
            )
        }
    }
}

private fun NavGraphBuilder.customerGraph(
    navController: NavHostController,
    sessionViewModel: SessionViewModel,
    user: AuthUser?,
    darkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
) {
    navigation(startDestination = CustomerRoutes.HOME, route = Graph.CUSTOMER) {
        composable(CustomerRoutes.HOME) {
            val customerId = AuthRepositoryProvider.instance.currentUserId().orEmpty()
            val homeViewModel: CustomerHomeViewModel = viewModel(
                factory = CustomerHomeViewModel.factory(
                    customerId = customerId,
                    repairRequestRepository = RepositoryProvider.repairRequests,
                    serviceRepository = RepositoryProvider.services,
                ),
            )
            // Re-checked every time Home is shown, so a repair booked (or
            // finished) while the customer was elsewhere is picked up on the
            // way back. Live status after that comes from the listener.
            LaunchedEffect(Unit) { homeViewModel.refresh() }
            val homeUiState by homeViewModel.uiState.collectAsState()
            CustomerHomeScreen(
                uiState = homeUiState,
                onBookRepair = { navController.navigateToTab(CustomerTab.BOOK) },
                onBrowseServices = { navController.navigateToTab(CustomerTab.SERVICES) },
                // A tile opens the catalog already filtered to that category,
                // as a normal push, so Back returns to Home.
                onCategorySelected = { category ->
                    navController.navigate(CustomerRoutes.catalog(category.name))
                },
                onTrackRepair = { requestId -> navController.navigate(CustomerRoutes.tracking(requestId)) },
                onViewRepairDetail = { requestId -> navController.navigate(CustomerRoutes.historyDetail(requestId)) },
                onViewAllRepairs = { navController.navigateToTab(CustomerTab.REPAIRS) },
            )
        }

        composable(
            route = CustomerRoutes.CATALOG,
            arguments = listOf(
                navArgument(CustomerRoutes.CATEGORY_ARG) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            // An unknown category name falls back to the unfiltered catalog
            // rather than crashing on valueOf.
            val initialCategory = backStackEntry.arguments
                ?.getString(CustomerRoutes.CATEGORY_ARG)
                ?.let { name -> DeviceCategory.entries.firstOrNull { it.name == name } }
            val viewModel: ServiceCatalogViewModel = viewModel(
                factory = ServiceCatalogViewModel.factory(RepositoryProvider.services, initialCategory),
            )
            val uiState by viewModel.uiState.collectAsState()
            ServiceCatalogScreen(
                uiState = uiState,
                onQueryChange = viewModel::onQueryChange,
                onCategorySelect = viewModel::onCategorySelect,
                onRetry = viewModel::load,
                onClearFilters = viewModel::clearFilters,
                onServiceClick = { service -> navController.navigate(CustomerRoutes.serviceDetail(service.id)) },
                onBack = { navController.popBackStack() },
                title = "Services",
                // Reached two ways: as the Services tab, where there is
                // nothing behind it, and pushed from a Home service tile with
                // a category, where Back returns to Home.
                showBack = initialCategory != null,
            )
        }

        // The Book Repair tab: the same catalog, in booking mode.
        composable(CustomerRoutes.BOOK_START) {
            val viewModel: ServiceCatalogViewModel = viewModel(
                factory = ServiceCatalogViewModel.factory(RepositoryProvider.services),
            )
            val uiState by viewModel.uiState.collectAsState()
            ServiceCatalogScreen(
                uiState = uiState,
                onQueryChange = viewModel::onQueryChange,
                onCategorySelect = viewModel::onCategorySelect,
                onRetry = viewModel::load,
                onClearFilters = viewModel::clearFilters,
                // The one difference that matters: a tap starts the booking
                // instead of opening the service detail.
                onServiceClick = { service -> navController.navigate(CustomerRoutes.bookRepair(service.id)) },
                onBack = { navController.popBackStack() },
                title = "Book a Repair",
                hint = "Pick the service you need and we'll take your device details next.",
                showBack = false,
            )
        }

        composable(
            route = CustomerRoutes.SERVICE_DETAIL,
            arguments = listOf(navArgument(CustomerRoutes.SERVICE_ID_ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            val serviceId = backStackEntry.arguments?.getString(CustomerRoutes.SERVICE_ID_ARG).orEmpty()
            val viewModel: ServiceDetailViewModel = viewModel(
                factory = ServiceDetailViewModel.factory(serviceId, RepositoryProvider.services),
            )
            val uiState by viewModel.uiState.collectAsState()
            ServiceDetailScreen(
                uiState = uiState,
                onRetry = viewModel::load,
                onBookRepair = { service -> navController.navigate(CustomerRoutes.bookRepair(service.id)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = CustomerRoutes.BOOK_REPAIR,
            arguments = listOf(navArgument(CustomerRoutes.SERVICE_ID_ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            val serviceId = backStackEntry.arguments?.getString(CustomerRoutes.SERVICE_ID_ARG).orEmpty()
            val ownerId = AuthRepositoryProvider.instance.currentUserId().orEmpty()
            val context = LocalContext.current
            val locationRepository = remember(context) { RepositoryProvider.location(context) }
            // The matching rule is a domain use case, composed here from the
            // repository interfaces rather than reached for inside the ViewModel.
            val matchBranches = remember {
                MatchBranchesUseCase(
                    branchRepository = RepositoryProvider.branches,
                    technicianRepository = RepositoryProvider.technicians,
                    sparePartRepository = RepositoryProvider.spareParts,
                )
            }
            val viewModel: BookRepairViewModel = viewModel(
                factory = BookRepairViewModel.factory(
                    serviceId = serviceId,
                    ownerId = ownerId,
                    serviceRepository = RepositoryProvider.services,
                    imageUploadRepository = RepositoryProvider.imageUpload,
                    locationRepository = locationRepository,
                    matchBranches = matchBranches,
                    repairRequestRepository = RepositoryProvider.repairRequests,
                    draftRepository = RepositoryProvider.draftRepairRequests,
                ),
            )
            val uiState by viewModel.uiState.collectAsState()
            BookRepairScreen(
                uiState = uiState,
                onBrandChange = viewModel::onBrandChange,
                onModelChange = viewModel::onModelChange,
                onSerialChange = viewModel::onSerialChange,
                onIssueChange = viewModel::onIssueChange,
                onNext = viewModel::goNext,
                onBack = { viewModel.goBack { navController.popBackStack() } },
                onAddImages = { uris -> viewModel.addImages(context, uris) },
                onRemoveImage = viewModel::removeImage,
                onRetryImage = { imageId -> viewModel.retryImage(context, imageId) },
                onCameraPermissionDenied = viewModel::onCameraPermissionDenied,
                onDismissPermissionMessage = viewModel::dismissPermissionMessage,
                onPhotoError = viewModel::onPhotoError,
                onDismissPhotoMessage = viewModel::dismissPhotoMessage,
                onDismissDraftRestoredMessage = viewModel::dismissDraftRestoredMessage,
                onLocationPermissionResult = viewModel::onLocationPermissionResult,
                onBranchSelected = viewModel::onBranchSelected,
                onScheduleChange = viewModel::onScheduleChange,
                onRetryMatching = viewModel::retryMatching,
                onSubmit = { viewModel.submit { /* the pane's Track button routes to Block 6's screen */ } },
                onDismissSubmitError = viewModel::dismissSubmitError,
                hasLocationPermission = viewModel::hasLocationPermission,
                onDone = {
                    // Back to the customer home, clearing the whole booking
                    // flow so Back can't re-enter a submitted request.
                    navController.navigate(CustomerRoutes.HOME) {
                        popUpTo(CustomerRoutes.HOME) { inclusive = true }
                    }
                },
                onTrackRepair = { requestId ->
                    // Same pop as Done, then straight into tracking, so Back
                    // from the timeline lands on Home rather than back inside
                    // the submitted booking.
                    navController.navigate(CustomerRoutes.HOME) {
                        popUpTo(CustomerRoutes.HOME) { inclusive = true }
                    }
                    navController.navigate(CustomerRoutes.tracking(requestId))
                },
            )
        }

        composable(
            route = CustomerRoutes.TRACKING,
            arguments = listOf(navArgument(CustomerRoutes.REQUEST_ID_ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            val requestId = backStackEntry.arguments?.getString(CustomerRoutes.REQUEST_ID_ARG).orEmpty()
            val trackingViewModel: RepairTrackingViewModel = viewModel(
                factory = RepairTrackingViewModel.factory(
                    requestId = requestId,
                    repairRequestRepository = RepositoryProvider.repairRequests,
                    serviceRepository = RepositoryProvider.services,
                    branchRepository = RepositoryProvider.branches,
                ),
            )
            val trackingUiState by trackingViewModel.uiState.collectAsState()
            RepairTrackingScreen(
                uiState = trackingUiState,
                onRetry = trackingViewModel::observe,
                onPayNow = { id -> navController.navigate(CustomerRoutes.payment(id)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(CustomerRoutes.HISTORY) {
            val customerId = AuthRepositoryProvider.instance.currentUserId().orEmpty()
            val historyViewModel: RepairHistoryViewModel = viewModel(
                factory = RepairHistoryViewModel.factory(
                    customerId = customerId,
                    repairRequestRepository = RepositoryProvider.repairRequests,
                    serviceRepository = RepositoryProvider.services,
                ),
            )
            // Same reason as Home: the tab keeps its ViewModel across tab
            // switches, so a repair booked in the meantime needs a re-read.
            LaunchedEffect(Unit) { historyViewModel.load() }
            val historyUiState by historyViewModel.uiState.collectAsState()
            RepairHistoryScreen(
                uiState = historyUiState,
                onRetry = historyViewModel::load,
                onRepairClick = { repair -> navController.navigate(CustomerRoutes.historyDetail(repair.id)) },
                onTrackRepair = { repair -> navController.navigate(CustomerRoutes.tracking(repair.id)) },
                onBrowseServices = { navController.navigateToTab(CustomerTab.SERVICES) },
                onBack = { navController.popBackStack() },
                showBack = false,
            )
        }

        composable(
            route = CustomerRoutes.HISTORY_DETAIL,
            arguments = listOf(navArgument(CustomerRoutes.REQUEST_ID_ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            val requestId = backStackEntry.arguments?.getString(CustomerRoutes.REQUEST_ID_ARG).orEmpty()
            val detailViewModel: RepairHistoryDetailViewModel = viewModel(
                factory = RepairHistoryDetailViewModel.factory(
                    requestId = requestId,
                    repairRequestRepository = RepositoryProvider.repairRequests,
                    serviceRepository = RepositoryProvider.services,
                    branchRepository = RepositoryProvider.branches,
                    paymentRepository = RepositoryProvider.payments,
                ),
            )
            val detailUiState by detailViewModel.uiState.collectAsState()
            RepairHistoryDetailScreen(
                uiState = detailUiState,
                onRetry = detailViewModel::load,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = CustomerRoutes.PAYMENT,
            arguments = listOf(navArgument(CustomerRoutes.REQUEST_ID_ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            val requestId = backStackEntry.arguments?.getString(CustomerRoutes.REQUEST_ID_ARG).orEmpty()
            val paymentViewModel: PaymentViewModel = viewModel(
                factory = PaymentViewModel.factory(
                    requestId = requestId,
                    repairRequestRepository = RepositoryProvider.repairRequests,
                    serviceRepository = RepositoryProvider.services,
                    paymentRepository = RepositoryProvider.payments,
                ),
            )
            val paymentUiState by paymentViewModel.uiState.collectAsState()
            PaymentScreen(
                uiState = paymentUiState,
                onRetryLoad = paymentViewModel::load,
                onStart = paymentViewModel::goToMethod,
                onMethodSelected = paymentViewModel::onMethodSelected,
                onContinueFromMethod = paymentViewModel::continueFromMethod,
                onCardNumberChange = paymentViewModel::onCardNumberChange,
                onExpiryChange = paymentViewModel::onExpiryChange,
                onCvvChange = paymentViewModel::onCvvChange,
                onCardNameChange = paymentViewModel::onCardNameChange,
                onSimulateFailureChange = paymentViewModel::onSimulateFailureChange,
                onPayNow = paymentViewModel::payNow,
                onRetryPayment = paymentViewModel::retry,
                onViewReceipt = paymentViewModel::viewReceipt,
                // The repair is COMPLETED by this point, so the tracking
                // screen behind it is finished with — Done goes to history
                // rather than back into the flow that just ended.
                onDone = {
                    navController.navigate(CustomerRoutes.HISTORY) {
                        popUpTo(CustomerRoutes.HOME)
                    }
                },
                onBack = { paymentViewModel.goBack { navController.popBackStack() } },
            )
        }

        composable(CustomerRoutes.PROFILE) { backStackEntry ->
            val context = LocalContext.current
            val profileViewModel: ProfileViewModel = viewModel(
                factory = ProfileViewModel.factory(
                    user,
                    AuthRepositoryProvider.instance,
                    RepositoryProvider.imageUpload,
                ),
            )
            LaunchedEffect(user) { profileViewModel.syncUser(user) }
            LaunchedEffect(Unit) { profileViewModel.refresh(sessionViewModel::signIn) }
            val savedFeedback by backStackEntry.savedStateHandle
                .getStateFlow<String?>(PROFILE_FEEDBACK_KEY, null)
                .collectAsState()
            LaunchedEffect(savedFeedback) {
                savedFeedback?.let {
                    profileViewModel.showMessage(it)
                    backStackEntry.savedStateHandle[PROFILE_FEEDBACK_KEY] = null
                }
            }
            val profileUiState by profileViewModel.uiState.collectAsState()
            val signOutScope = rememberCoroutineScope()
            ProfileScreen(
                uiState = profileUiState,
                appVersion = BuildConfig.VERSION_NAME,
                darkTheme = darkTheme,
                onThemeChange = onThemeChange,
                onRetry = { profileViewModel.refresh(sessionViewModel::signIn) },
                onEditProfile = { navController.navigate(CustomerRoutes.EDIT_PROFILE) },
                onPhotoSelected = { uri -> profileViewModel.updatePhoto(context, uri, sessionViewModel::signIn) },
                onRemovePhoto = { profileViewModel.removePhoto(sessionViewModel::signIn) },
                onPhotoMessage = profileViewModel::showMessage,
                onDismissMessage = profileViewModel::clearFeedback,
                onBrowseServices = { navController.navigateToTab(CustomerTab.SERVICES) },
                onViewRepairs = { navController.navigateToTab(CustomerTab.REPAIRS) },
                onSignOut = {
                    // Sign-out moved here from Home with the bottom bar. Same
                    // behaviour as before: the saved draft holds this
                    // customer's device and issue text, and there is no reason
                    // to leave it on the device once they sign out.
                    signOutScope.launch { RepositoryProvider.draftRepairRequests.clear() }
                    AuthRepositoryProvider.instance.signOut()
                    sessionViewModel.signOut()
                    navController.navigate(Graph.AUTH) {
                        popUpTo(Graph.CUSTOMER) { inclusive = true }
                    }
                },
            )
        }

        composable(CustomerRoutes.EDIT_PROFILE) {
            val context = LocalContext.current
            val profileViewModel: ProfileViewModel = viewModel(
                factory = ProfileViewModel.factory(
                    user,
                    AuthRepositoryProvider.instance,
                    RepositoryProvider.imageUpload,
                ),
            )
            LaunchedEffect(user) { profileViewModel.syncUser(user) }
            val profileUiState by profileViewModel.uiState.collectAsState()
            EditProfileScreen(
                uiState = profileUiState,
                onNameChange = profileViewModel::onNameChange,
                onPhoneChange = profileViewModel::onPhoneChange,
                onNameFocusLost = profileViewModel::onNameFocusLost,
                onPhoneFocusLost = profileViewModel::onPhoneFocusLost,
                onSave = {
                    profileViewModel.save(
                        onUpdated = sessionViewModel::signIn,
                        onSaved = {
                            navController.previousBackStackEntry?.savedStateHandle?.set(
                                PROFILE_FEEDBACK_KEY,
                                "Your profile has been updated.",
                            )
                            navController.popBackStack()
                        },
                    )
                },
                onDiscardChanges = profileViewModel::resetForm,
                onBack = { navController.popBackStack() },
                onPhotoSelected = { uri -> profileViewModel.updatePhoto(context, uri, sessionViewModel::signIn) },
                onRemovePhoto = { profileViewModel.removePhoto(sessionViewModel::signIn) },
                onPhotoMessage = profileViewModel::showMessage,
                onDismissMessage = profileViewModel::clearFeedback,
            )
        }
    }
}

private const val PROFILE_FEEDBACK_KEY = "profile_feedback"

/**
 * The one staff screen set, shared by Admin, Branch Manager, and Technician.
 * There is no per-role graph — every difference is a flag on [StaffContext],
 * which is built from the `users/{uid}` record the session already holds.
 */
private fun NavGraphBuilder.staffGraph(
    navController: NavHostController,
    sessionViewModel: SessionViewModel,
    user: AuthUser?,
) {
    navigation(startDestination = StaffRoutes.DASHBOARD, route = Graph.STAFF) {
        composable(StaffRoutes.DASHBOARD) {
            val staffContext = StaffContext.from(user)
            val dashboardViewModel: StaffDashboardViewModel = viewModel(
                factory = StaffDashboardViewModel.factory(
                    staffContext = staffContext,
                    repairRequestRepository = RepositoryProvider.repairRequests,
                    paymentRepository = RepositoryProvider.payments,
                    branchRepository = RepositoryProvider.branches,
                    technicianRepository = RepositoryProvider.technicians,
                    sparePartRepository = RepositoryProvider.spareParts,
                    userRepository = RepositoryProvider.users,
                ),
            )
            // Re-read on every return so a repair confirmed or advanced on
            // another screen is reflected in the counts.
            LaunchedEffect(Unit) { dashboardViewModel.load() }
            val dashboardUiState by dashboardViewModel.uiState.collectAsState()

            var branchName by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(staffContext.branchId) {
                branchName = staffContext.branchId
                    ?.let { RepositoryProvider.branches.getBranch(it).getOrNull()?.name }
            }

            StaffDashboardScreen(
                staffContext = staffContext,
                uiState = dashboardUiState,
                branchName = branchName,
                onRetry = dashboardViewModel::load,
                onOpenQueue = { tab -> navController.navigate(StaffRoutes.queue(tab.name)) },
                onOpenInventory = { navController.navigate(StaffRoutes.INVENTORY) },
                onSignOut = {
                    AuthRepositoryProvider.instance.signOut()
                    sessionViewModel.signOut()
                    navController.navigate(Graph.AUTH) {
                        popUpTo(Graph.STAFF) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = StaffRoutes.QUEUE,
            arguments = listOf(
                navArgument(StaffRoutes.TAB_ARG) {
                    type = NavType.StringType
                    defaultValue = StaffQueueTab.NEW.name
                },
            ),
        ) { backStackEntry ->
            val staffContext = StaffContext.from(user)
            val requestedTab = backStackEntry.arguments?.getString(StaffRoutes.TAB_ARG)
            val initialTab = runCatching { StaffQueueTab.valueOf(requestedTab.orEmpty()) }
                .getOrDefault(StaffQueueTab.NEW)
                // A Technician has no New tab, so a link to it lands on Active.
                .let { if (!staffContext.canAssign) StaffQueueTab.ACTIVE else it }

            val queueViewModel: StaffAppointmentsViewModel = viewModel(
                factory = StaffAppointmentsViewModel.factory(
                    staffContext = staffContext,
                    repairRequestRepository = RepositoryProvider.repairRequests,
                    serviceRepository = RepositoryProvider.services,
                    branchRepository = RepositoryProvider.branches,
                    technicianRepository = RepositoryProvider.technicians,
                    initialTab = initialTab,
                ),
            )
            LaunchedEffect(Unit) { queueViewModel.load() }
            val queueUiState by queueViewModel.uiState.collectAsState()

            StaffAppointmentsScreen(
                staffContext = staffContext,
                uiState = queueUiState,
                onTabSelected = queueViewModel::onTabSelected,
                onQueryChanged = queueViewModel::onQueryChanged,
                onRetry = queueViewModel::load,
                onRequestClick = { request ->
                    navController.navigate(StaffRoutes.appointment(request.id))
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = StaffRoutes.APPOINTMENT,
            arguments = listOf(navArgument(StaffRoutes.REQUEST_ID_ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            val staffContext = StaffContext.from(user)
            val requestId = backStackEntry.arguments?.getString(StaffRoutes.REQUEST_ID_ARG).orEmpty()
            // Same use case the customer's booking ran in Block 5, composed
            // here from the repository interfaces rather than duplicated.
            val matchBranches = remember {
                MatchBranchesUseCase(
                    branchRepository = RepositoryProvider.branches,
                    technicianRepository = RepositoryProvider.technicians,
                    sparePartRepository = RepositoryProvider.spareParts,
                )
            }
            val detailViewModel: StaffAppointmentDetailViewModel = viewModel(
                factory = StaffAppointmentDetailViewModel.factory(
                    requestId = requestId,
                    staffContext = staffContext,
                    repairRequestRepository = RepositoryProvider.repairRequests,
                    serviceRepository = RepositoryProvider.services,
                    matchBranches = matchBranches,
                    technicianRepository = RepositoryProvider.technicians,
                ),
            )
            val detailUiState by detailViewModel.uiState.collectAsState()

            StaffAppointmentDetailScreen(
                staffContext = staffContext,
                uiState = detailUiState,
                onRetry = detailViewModel::load,
                onBranchSelected = detailViewModel::onBranchSelected,
                onTechnicianSelected = detailViewModel::onTechnicianSelected,
                onConfirmAssignment = { detailViewModel.confirmAssignment() },
                onAdvanceStatus = detailViewModel::advanceStatus,
                onMessageShown = detailViewModel::dismissMessages,
                onBack = { navController.popBackStack() },
            )
        }

        composable(StaffRoutes.INVENTORY) {
            val staffContext = StaffContext.from(user)
            if (staffContext.canManageInventory) {
                val inventoryViewModel: AdminInventoryViewModel = viewModel(
                    factory = AdminInventoryViewModel.factory(
                        staffContext = staffContext,
                        repository = RepositoryProvider.adminInventory,
                    ),
                )
                val inventoryUiState by inventoryViewModel.uiState.collectAsState()
                AdminInventoryScreen(
                    uiState = inventoryUiState,
                    onQueryChange = inventoryViewModel::onQueryChange,
                    onBranchSelected = inventoryViewModel::onBranchSelected,
                    onCategorySelected = inventoryViewModel::onCategorySelected,
                    onStockFilterSelected = inventoryViewModel::onStockFilterSelected,
                    onSortSelected = inventoryViewModel::onSortSelected,
                    onOpenDetails = inventoryViewModel::openDetails,
                    onCloseDetails = inventoryViewModel::closeDetails,
                    onOpenCreate = inventoryViewModel::openCreateItem,
                    onOpenEdit = inventoryViewModel::openEditItem,
                    onUpdateItemForm = inventoryViewModel::updateItemForm,
                    onCloseItemForm = inventoryViewModel::closeItemForm,
                    onSaveItem = inventoryViewModel::saveItem,
                    onOpenAdjustment = inventoryViewModel::openAdjustment,
                    onUpdateAdjustmentForm = inventoryViewModel::updateAdjustmentForm,
                    onCloseAdjustment = inventoryViewModel::closeAdjustment,
                    onSaveAdjustment = inventoryViewModel::saveAdjustment,
                    onRequestArchive = inventoryViewModel::requestArchive,
                    onDismissArchive = inventoryViewModel::dismissArchive,
                    onConfirmArchive = inventoryViewModel::confirmArchive,
                    onRestore = inventoryViewModel::restoreItem,
                    onRetry = inventoryViewModel::load,
                    onMessageShown = inventoryViewModel::dismissMessages,
                    onBack = { navController.popBackStack() },
                )
            } else {
                val inventoryViewModel: StaffInventoryViewModel = viewModel(
                    factory = StaffInventoryViewModel.factory(
                        staffContext = staffContext,
                        branchRepository = RepositoryProvider.branches,
                        technicianRepository = RepositoryProvider.technicians,
                        sparePartRepository = RepositoryProvider.spareParts,
                        repairRequestRepository = RepositoryProvider.repairRequests,
                        userRepository = RepositoryProvider.users,
                        initialTab = StaffInventoryTab.PARTS,
                    ),
                )
                val inventoryUiState by inventoryViewModel.uiState.collectAsState()

                StaffInventoryScreen(
                    staffContext = staffContext,
                    uiState = inventoryUiState,
                    onTabSelected = inventoryViewModel::onTabSelected,
                    onBranchSelected = inventoryViewModel::onBranchSelected,
                    onStockChange = inventoryViewModel::updateStock,
                    onOpenCreateTechnician = inventoryViewModel::openCreateTechnician,
                    onOpenEditTechnician = inventoryViewModel::openEditTechnician,
                    onRequestArchiveTechnician = inventoryViewModel::requestArchiveTechnician,
                    onDismissTechnicianForm = inventoryViewModel::dismissTechnicianForm,
                    onTechnicianNameChange = inventoryViewModel::onTechnicianNameChange,
                    onTechnicianBranchChange = inventoryViewModel::onTechnicianBranchChange,
                    onTechnicianSkillToggle = inventoryViewModel::onTechnicianSkillToggle,
                    onTechnicianAvailableChange = inventoryViewModel::onTechnicianAvailableChange,
                    onSaveTechnician = inventoryViewModel::saveTechnician,
                    onDismissArchiveTechnician = inventoryViewModel::dismissArchiveTechnician,
                    onConfirmArchiveTechnician = inventoryViewModel::confirmArchiveTechnician,
                    onRetry = inventoryViewModel::load,
                    onMessageShown = inventoryViewModel::dismissMessages,
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(StaffRoutes.TECHNICIANS) {
            val staffContext = StaffContext.from(user)
            val inventoryViewModel: StaffInventoryViewModel = viewModel(
                factory = StaffInventoryViewModel.factory(
                    staffContext = staffContext,
                    branchRepository = RepositoryProvider.branches,
                    technicianRepository = RepositoryProvider.technicians,
                    sparePartRepository = RepositoryProvider.spareParts,
                    repairRequestRepository = RepositoryProvider.repairRequests,
                    userRepository = RepositoryProvider.users,
                    initialTab = StaffInventoryTab.TECHNICIANS,
                ),
            )
            val inventoryUiState by inventoryViewModel.uiState.collectAsState()
            StaffInventoryScreen(
                staffContext, inventoryUiState, inventoryViewModel::onTabSelected,
                inventoryViewModel::onBranchSelected, inventoryViewModel::updateStock,
                inventoryViewModel::openCreateTechnician, inventoryViewModel::openEditTechnician,
                inventoryViewModel::requestArchiveTechnician, inventoryViewModel::dismissTechnicianForm,
                inventoryViewModel::onTechnicianNameChange, inventoryViewModel::onTechnicianBranchChange,
                inventoryViewModel::onTechnicianSkillToggle, inventoryViewModel::onTechnicianAvailableChange,
                inventoryViewModel::saveTechnician, inventoryViewModel::dismissArchiveTechnician,
                inventoryViewModel::confirmArchiveTechnician, inventoryViewModel::load,
                inventoryViewModel::dismissMessages, { navController.popBackStack() },
            )
        }

        composable(StaffRoutes.MORE) {
            val staffContext = StaffContext.from(user)
            StaffMoreScreen(
                staffContext = staffContext,
                onBranches = { navController.navigate(StaffRoutes.BRANCHES) },
                onUsers = { navController.navigate(StaffRoutes.USERS) },
                onReports = { navController.navigate(StaffRoutes.REPORTS) },
                onSignOut = {
                    AuthRepositoryProvider.instance.signOut()
                    sessionViewModel.signOut()
                    navController.navigate(Graph.AUTH) { popUpTo(Graph.STAFF) { inclusive = true } }
                },
            )
        }

        listOf(StaffRoutes.BRANCHES, StaffRoutes.USERS, StaffRoutes.REPORTS).forEach { route ->
            composable(route) {
                val staffContext = StaffContext.from(user)
                val operationsViewModel: StaffOperationsViewModel = viewModel(
                    factory = StaffOperationsViewModel.factory(
                        staffContext, RepositoryProvider.repairRequests, RepositoryProvider.payments,
                        RepositoryProvider.branches, RepositoryProvider.technicians,
                        RepositoryProvider.spareParts, RepositoryProvider.users,
                    ),
                )
                val state by operationsViewModel.uiState.collectAsState()
                when (route) {
                    StaffRoutes.BRANCHES -> StaffBranchesScreen(staffContext, state, operationsViewModel::load) { navController.popBackStack() }
                    StaffRoutes.USERS -> StaffUsersScreen(staffContext, state, operationsViewModel::load) { navController.popBackStack() }
                    else -> StaffReportsScreen(staffContext, state, operationsViewModel::load) { navController.popBackStack() }
                }
            }
        }
    }
}
