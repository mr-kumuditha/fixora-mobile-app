package com.techfix.app.ui.staff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.techfix.app.domain.branch.Branch
import com.techfix.app.domain.branch.BranchRepository
import com.techfix.app.domain.catalog.DeviceCategory
import com.techfix.app.domain.sparepart.SparePart
import com.techfix.app.domain.sparepart.SparePartRepository
import com.techfix.app.domain.technician.Technician
import com.techfix.app.domain.technician.TechnicianRepository
import com.techfix.app.domain.repair.RepairRequestRepository
import com.techfix.app.domain.user.UserRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class StaffInventoryTab { TECHNICIANS, PARTS }

/** One part with the quantity held at the branch currently on screen. */
data class PartStockRow(
    val part: SparePart,
    val quantity: Int,
)

data class TechnicianRosterDetails(
    val email: String? = null,
    val photoUrl: String? = null,
    val accountLinked: Boolean = false,
    val assignedRepairCount: Int = 0,
)

data class StaffInventoryUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val tab: StaffInventoryTab = StaffInventoryTab.TECHNICIANS,
    val branches: List<Branch> = emptyList(),
    val selectedBranchId: String? = null,
    val technicians: List<Technician> = emptyList(),
    val technicianDetails: Map<String, TechnicianRosterDetails> = emptyMap(),
    val parts: List<PartStockRow> = emptyList(),
    /** Part id currently being written, so only that row shows a spinner. */
    val savingPartId: String? = null,
    val actionError: String? = null,
    val confirmationMessage: String? = null,
    val technicianFormVisible: Boolean = false,
    val editingTechnicianId: String? = null,
    val formName: String = "",
    val formBranchId: String? = null,
    val formSkills: Set<DeviceCategory> = emptySet(),
    val formAvailable: Boolean = true,
    val formError: String? = null,
    val savingTechnician: Boolean = false,
    val archiveTechnician: Technician? = null,
) {
    val canSwitchBranch: Boolean get() = branches.size > 1
}

/**
 * Technician & Spare Parts — list, plus a stock-level correction, and
 * nothing else, per the brief.
 *
 * Technicians come from Firestore; spare parts and stock remain in Supabase.
 * Admins manage the roster through [TechnicianRepository], while other staff
 * roles retain a read-only view. Stock remains gated by [StaffContext.canEditStock].
 */
class StaffInventoryViewModel(
    private val staffContext: StaffContext,
    private val branchRepository: BranchRepository,
    private val technicianRepository: TechnicianRepository,
    private val sparePartRepository: SparePartRepository,
    private val repairRequestRepository: RepairRequestRepository,
    private val userRepository: UserRepository,
    initialTab: StaffInventoryTab = StaffInventoryTab.TECHNICIANS,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StaffInventoryUiState(tab = initialTab))
    val uiState: StateFlow<StaffInventoryUiState> = _uiState

    val canEditStock: Boolean get() = staffContext.canEditStock

    init {
        load()
    }

    fun onTabSelected(tab: StaffInventoryTab) {
        _uiState.update { it.copy(tab = tab, actionError = null, confirmationMessage = null) }
    }

    fun onBranchSelected(branchId: String) {
        if (branchId == _uiState.value.selectedBranchId) return
        _uiState.update { it.copy(selectedBranchId = branchId, isLoading = true) }
        loadBranchScopedData(branchId)
    }

    fun openCreateTechnician() {
        if (staffContext.role != com.techfix.app.core.navigation.UserRole.ADMIN) return
        _uiState.update {
            it.copy(
                technicianFormVisible = true,
                editingTechnicianId = null,
                formName = "",
                formBranchId = it.selectedBranchId,
                formSkills = emptySet(),
                formAvailable = true,
                formError = null,
            )
        }
    }

    fun openEditTechnician(technician: Technician) {
        if (staffContext.role != com.techfix.app.core.navigation.UserRole.ADMIN) return
        _uiState.update {
            it.copy(
                technicianFormVisible = true,
                editingTechnicianId = technician.id,
                formName = technician.name,
                formBranchId = technician.branchId,
                formSkills = technician.categorySkills.toSet(),
                formAvailable = technician.available,
                formError = null,
            )
        }
    }

    fun dismissTechnicianForm() = _uiState.update { it.copy(technicianFormVisible = false, formError = null) }

    fun onTechnicianNameChange(value: String) = _uiState.update { it.copy(formName = value, formError = null) }

    fun onTechnicianBranchChange(value: String) = _uiState.update { it.copy(formBranchId = value, formError = null) }

    fun onTechnicianSkillToggle(category: DeviceCategory) = _uiState.update {
        it.copy(formSkills = if (category in it.formSkills) it.formSkills - category else it.formSkills + category, formError = null)
    }

    fun onTechnicianAvailableChange(value: Boolean) = _uiState.update { it.copy(formAvailable = value) }

    fun saveTechnician() {
        if (staffContext.role != com.techfix.app.core.navigation.UserRole.ADMIN) return
        val state = _uiState.value
        val branchId = state.formBranchId
        if (state.formName.trim().isBlank()) {
            _uiState.update { it.copy(formError = "Name is required") }
            return
        }
        if (branchId == null) {
            _uiState.update { it.copy(formError = "Choose a branch") }
            return
        }
        if (state.formSkills.isEmpty()) {
            _uiState.update { it.copy(formError = "Select at least one skill") }
            return
        }
        _uiState.update { it.copy(savingTechnician = true, formError = null) }
        viewModelScope.launch {
            val result = state.editingTechnicianId?.let { id ->
                technicianRepository.updateTechnician(id, state.formName, branchId, state.formSkills.toList(), state.formAvailable)
            } ?: technicianRepository.createTechnician(state.formName, branchId, state.formSkills.toList(), state.formAvailable)
            result.onSuccess {
                val visibleBranchId = _uiState.value.selectedBranchId ?: branchId
                val editedId = state.editingTechnicianId
                technicianRepository.getTechniciansForBranch(visibleBranchId)
                    .onSuccess { persistedTechnicians ->
                        _uiState.update { current ->
                            current.copy(
                                technicianFormVisible = false,
                                savingTechnician = false,
                                technicians = persistedTechnicians,
                                confirmationMessage = if (editedId == null) {
                                    "Technician created."
                                } else {
                                    "Technician updated."
                                },
                            )
                        }
                    }
                    .onFailure {
                        _uiState.update {
                            it.copy(
                                savingTechnician = false,
                                formError = TECHNICIAN_SAVE_ERROR,
                            )
                        }
                    }
            }.onFailure {
                _uiState.update {
                    it.copy(
                        savingTechnician = false,
                        formError = TECHNICIAN_SAVE_ERROR,
                    )
                }
            }
        }
    }

    fun requestArchiveTechnician(technician: Technician) {
        if (staffContext.role == com.techfix.app.core.navigation.UserRole.ADMIN) {
            _uiState.update { it.copy(archiveTechnician = technician) }
        }
    }

    fun dismissArchiveTechnician() = _uiState.update { it.copy(archiveTechnician = null) }

    fun confirmArchiveTechnician() {
        if (staffContext.role != com.techfix.app.core.navigation.UserRole.ADMIN) return
        val technician = _uiState.value.archiveTechnician ?: return
        _uiState.update { it.copy(archiveTechnician = null, savingTechnician = true) }
        viewModelScope.launch {
            technicianRepository.archiveTechnician(technician.id)
                .onSuccess {
                    _uiState.update { it.copy(savingTechnician = false, confirmationMessage = "Technician archived safely.") }
                    _uiState.value.selectedBranchId?.let(::loadBranchScopedData)
                }
                .onFailure {
                    _uiState.update {
                        it.copy(
                            savingTechnician = false,
                            actionError = "Unable to archive technician. Please try again.",
                        )
                    }
                }
        }
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            branchRepository.getBranches()
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = error.message ?: "Couldn't load branches")
                    }
                }
                .onSuccess { allBranches ->
                    // A Branch Manager or Technician only ever sees their own
                    // branch here; an Admin gets the picker.
                    val visible = if (staffContext.seesAllBranches) {
                        allBranches
                    } else {
                        allBranches.filter { it.id == staffContext.branchId }
                            .ifEmpty { allBranches }
                    }
                    val selected = _uiState.value.selectedBranchId
                        ?.takeIf { id -> visible.any { it.id == id } }
                        ?: visible.firstOrNull()?.id

                    _uiState.update {
                        it.copy(branches = visible, selectedBranchId = selected)
                    }
                    if (selected == null) {
                        _uiState.update { it.copy(isLoading = false, errorMessage = "No branch to show") }
                    } else {
                        loadBranchScopedData(selected)
                    }
                }
        }
    }

    private fun loadBranchScopedData(branchId: String) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val techniciansDeferred = async { technicianRepository.getTechniciansForBranch(branchId) }
            val partsDeferred = async { sparePartRepository.getSpareParts() }
            val stockDeferred = async { sparePartRepository.getStockForBranch(branchId) }
            val usersDeferred = async {
                if (staffContext.role == com.techfix.app.core.navigation.UserRole.ADMIN) {
                    userRepository.getUsers()
                } else {
                    Result.success(emptyList())
                }
            }
            val repairsDeferred = async {
                if (staffContext.role == com.techfix.app.core.navigation.UserRole.ADMIN) {
                    repairRequestRepository.getAllRepairRequests()
                } else {
                    Result.success(emptyList())
                }
            }

            val technicians = techniciansDeferred.await()
            val parts = partsDeferred.await()
            val stock = stockDeferred.await()
            val users = usersDeferred.await()
            val repairs = repairsDeferred.await()

            val failure = technicians.exceptionOrNull()
                ?: parts.exceptionOrNull()
                ?: stock.exceptionOrNull()
                ?: users.exceptionOrNull()
                ?: repairs.exceptionOrNull()
            if (failure != null) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = failure.message ?: "Couldn't load inventory")
                }
                return@launch
            }

            val quantityByPartId = stock.getOrDefault(emptyList()).associate { it.partId to it.quantity }
            val persistedTechnicians = technicians.getOrDefault(emptyList()).sortedBy { it.name }
            val persistedUsers = users.getOrDefault(emptyList())
            val persistedRepairs = repairs.getOrDefault(emptyList())
            val details = persistedTechnicians.associate { technician ->
                val account = technician.linkedUserId?.let { linkedUid ->
                    persistedUsers.firstOrNull { user -> user.uid == linkedUid }
                }
                val linkIsValid = account?.role == com.techfix.app.core.navigation.UserRole.TECHNICIAN &&
                    account.technicianId == technician.id &&
                    account.branchId == technician.branchId
                technician.id to TechnicianRosterDetails(
                    email = account?.email,
                    photoUrl = account?.photoUrl,
                    accountLinked = linkIsValid,
                    assignedRepairCount = persistedRepairs.count { it.technicianId == technician.id },
                )
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = null,
                    technicians = persistedTechnicians,
                    technicianDetails = details,
                    // A part with no stock row at this branch reads as 0, not
                    // as absent — same rule the matching logic relies on.
                    parts = parts.getOrDefault(emptyList())
                        .sortedBy { part -> part.name }
                        .map { part -> PartStockRow(part, quantityByPartId[part.id] ?: 0) },
                )
            }
        }
    }

    fun updateStock(partId: String, quantity: Int) {
        if (!staffContext.canEditStock) {
            _uiState.update { it.copy(actionError = "Your role can't change stock levels.") }
            return
        }
        val branchId = _uiState.value.selectedBranchId ?: return
        if (quantity < 0) return

        _uiState.update { it.copy(savingPartId = partId, actionError = null, confirmationMessage = null) }
        viewModelScope.launch {
            sparePartRepository.updateStock(partId, branchId, quantity)
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            savingPartId = null,
                            parts = state.parts.map { row ->
                                if (row.part.id == partId) row.copy(quantity = quantity) else row
                            },
                            confirmationMessage = "Stock updated.",
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            savingPartId = null,
                            actionError = error.message ?: "Couldn't update stock",
                        )
                    }
                }
        }
    }

    fun dismissMessages() {
        _uiState.update { it.copy(actionError = null, confirmationMessage = null) }
    }

    companion object {
        private const val TECHNICIAN_SAVE_ERROR = "Unable to save technician changes. Please try again."

        fun factory(
            staffContext: StaffContext,
            branchRepository: BranchRepository,
            technicianRepository: TechnicianRepository,
            sparePartRepository: SparePartRepository,
            repairRequestRepository: RepairRequestRepository,
            userRepository: UserRepository,
            initialTab: StaffInventoryTab = StaffInventoryTab.TECHNICIANS,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    StaffInventoryViewModel(
                        staffContext = staffContext,
                        branchRepository = branchRepository,
                        technicianRepository = technicianRepository,
                        sparePartRepository = sparePartRepository,
                        repairRequestRepository = repairRequestRepository,
                        userRepository = userRepository,
                        initialTab = initialTab,
                    ) as T
            }
    }
}
