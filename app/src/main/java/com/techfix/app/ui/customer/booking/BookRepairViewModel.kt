package com.techfix.app.ui.customer.booking

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.techfix.app.core.util.ImageCompressor
import com.techfix.app.core.util.UploadDiagnostics
import com.techfix.app.domain.catalog.DeviceCategory
import com.techfix.app.domain.catalog.RepairService
import com.techfix.app.domain.catalog.ServiceRepository
import com.techfix.app.domain.draft.DraftImage
import com.techfix.app.domain.draft.DraftRepairRequest
import com.techfix.app.domain.draft.DraftRepairRequestRepository
import com.techfix.app.domain.location.Coordinates
import com.techfix.app.domain.location.LocationRepository
import com.techfix.app.domain.location.LocationResult
import com.techfix.app.domain.matching.BranchMatchResult
import com.techfix.app.domain.matching.MatchBranchesUseCase
import com.techfix.app.domain.repair.DeviceDetails
import com.techfix.app.domain.repair.RepairRequest
import com.techfix.app.domain.repair.RepairRequestRepository
import com.techfix.app.domain.repair.RepairStatus
import com.techfix.app.domain.storage.ImageUploadRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import java.util.UUID

enum class ImageUploadStatus { UPLOADING, UPLOADED, FAILED }

/**
 * How the customer's position was resolved. Denied and unavailable are shown
 * differently on the branch picker — one is fixable by the customer, the
 * other isn't — but neither blocks matching.
 */
enum class LocationStatus { IDLE, RESOLVING, AVAILABLE, PERMISSION_DENIED, UNAVAILABLE }

data class BookingImage(
    val id: String = UUID.randomUUID().toString(),
    val localUri: Uri,
    val remoteUrl: String? = null,
    val status: ImageUploadStatus = ImageUploadStatus.UPLOADING,
) {
    /**
     * What the thumbnail loads. The uploaded Supabase URL is preferred over
     * the local URI because a draft restored after the process was killed no
     * longer holds read permission on the picker's `content://` URI — the
     * remote URL still resolves.
     */
    val thumbnailModel: Any
        get() = remoteUrl ?: localUri
}

data class BookRepairUiState(
    val serviceLoading: Boolean = true,
    val serviceError: String? = null,
    val service: RepairService? = null,
    val step: Int = 1,
    val category: DeviceCategory? = null,
    val brand: String = "",
    val model: String = "",
    val serialNumber: String = "",
    val issueDescription: String = "",
    val images: List<BookingImage> = emptyList(),
    val photoMessage: String? = null,
    val permissionDeniedMessage: String? = null,
    /** A saved draft was restored into this flow — shown once as a snackbar. */
    val draftRestoredMessage: String? = null,
    // ---- Step 4: branch matching, schedule, submit ------------------------
    val locationStatus: LocationStatus = LocationStatus.IDLE,
    val customerLocation: Coordinates? = null,
    val matchLoading: Boolean = false,
    val matchError: String? = null,
    val matchResult: BranchMatchResult? = null,
    val selectedBranchId: String? = null,
    val scheduledAt: Long? = null,
    val submitting: Boolean = false,
    val submitError: String? = null,
    val submittedRequestId: String? = null,
) {
    val canAdvanceFromStep1: Boolean
        get() = category != null && brand.isNotBlank() && model.isNotBlank()

    val canAdvanceFromStep2: Boolean
        get() = issueDescription.trim().length >= 10

    val isUploadingAnyImage: Boolean
        get() = images.any { it.status == ImageUploadStatus.UPLOADING }

    val hasFailedImages: Boolean
        get() = images.any { it.status == ImageUploadStatus.FAILED }

    val remainingPhotoSlots: Int
        get() = BookingPhotoPolicy.remainingSlots(images.size)

    /**
     * At least one photo is uploaded and none are still in flight — a photo
     * that hasn't finished uploading has no Supabase URL to submit.
     */
    val canAdvanceFromStep3: Boolean
        get() = BookingPhotoPolicy.canContinue(images.map { it.status })

    val canSubmit: Boolean
        get() = selectedBranchId != null && scheduledAt != null && !submitting && !matchLoading

    /** Uploaded image URLs, in pick order — what gets stored on the request. */
    val uploadedImageUrls: List<String>
        get() = images.mapNotNull { it.remoteUrl }
}

/**
 * Drives the whole Book Repair flow: device details, issue, photos, then the
 * GPS branch match, schedule, and submit.
 *
 * The matching rule itself is deliberately not here — it is
 * [MatchBranchesUseCase] in the domain layer (architecture doc section 6).
 * This ViewModel only resolves a location, hands it to the use case, and
 * renders the result.
 */
class BookRepairViewModel(
    private val serviceId: String,
    private val ownerId: String,
    private val serviceRepository: ServiceRepository,
    private val imageUploadRepository: ImageUploadRepository,
    private val locationRepository: LocationRepository,
    private val matchBranches: MatchBranchesUseCase,
    private val repairRequestRepository: RepairRequestRepository,
    private val draftRepository: DraftRepairRequestRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookRepairUiState(scheduledAt = defaultScheduleSlot()))
    val uiState: StateFlow<BookRepairUiState> = _uiState

    /**
     * Set once the stored draft has been read (or found not to exist).
     * Autosaving only starts after that, so the empty initial state can never
     * overwrite a draft that is still being loaded.
     */
    private var draftLoaded = false

    init {
        loadService()
        restoreDraftThenAutosave()
    }

    private fun loadService() {
        _uiState.update { it.copy(serviceLoading = true, serviceError = null) }
        viewModelScope.launch {
            serviceRepository.getService(serviceId)
                .onSuccess { service ->
                    _uiState.update {
                        // The draft's category wins if it restored one — it is
                        // the same value, since a booking always starts from a
                        // service, but this keeps the restore authoritative.
                        it.copy(
                            serviceLoading = false,
                            service = service,
                            category = it.category ?: service.category,
                        )
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(serviceLoading = false, serviceError = "Unable to load this service. Please try again.")
                    }
                }
        }
    }

    /**
     * Reads back an interrupted booking for this same customer and service,
     * then starts saving every later change.
     */
    private fun restoreDraftThenAutosave() {
        viewModelScope.launch {
            val draft = runCatching { draftRepository.load(ownerId, serviceId) }.getOrNull()
            if (draft != null && !draft.isBlank) {
                _uiState.update { state ->
                    state.copy(
                        step = draft.step.coerceIn(1, LAST_STEP),
                        category = draft.category ?: state.category,
                        brand = draft.brand,
                        model = draft.model,
                        serialNumber = draft.serialNumber,
                        issueDescription = draft.issueDescription,
                        images = draft.images.map { it.toBookingImage() },
                        selectedBranchId = draft.selectedBranchId,
                        scheduledAt = draft.scheduledAt ?: state.scheduledAt,
                        draftRestoredMessage = restoredMessage(draft.unsavedImageCount),
                    )
                }
            }
            draftLoaded = true
            autosaveDraft()
        }
    }

    /**
     * One collector on the state, debounced, rather than a save call sprinkled
     * through every `on…Change`. Debounced because step 1 and 2 are text
     * fields and a write per keystroke would be pointless disk traffic;
     * 400ms is short enough that a process death costs at most one word.
     */
    @OptIn(FlowPreview::class)
    private fun autosaveDraft() {
        viewModelScope.launch {
            _uiState
                .map { it.toDraft() }
                .distinctUntilChanged()
                .debounce(DRAFT_SAVE_DEBOUNCE_MS)
                .collect { draft ->
                    when {
                        // Nothing to persist yet, or already submitted.
                        draft == null -> Unit
                        // The customer emptied the form again. Dropping the
                        // row matters: leaving it would resurrect fields they
                        // had deliberately cleared.
                        draft.isBlank -> draftRepository.clear()
                        else -> draftRepository.save(draft)
                    }
                }
        }
    }

    /**
     * Null means "don't touch storage at all": the stored draft hasn't been
     * read back yet, the booking is already submitted, or there is no signed-in
     * customer to own it. An empty-but-non-null draft is different — that is
     * the customer having cleared the form, and the collector clears the row.
     * Only photos that finished uploading are carried — see [DraftImage].
     */
    private fun BookRepairUiState.toDraft(): DraftRepairRequest? {
        if (!draftLoaded || submittedRequestId != null || ownerId.isBlank()) return null
        return DraftRepairRequest(
            customerId = ownerId,
            serviceId = serviceId,
            step = step,
            category = category,
            brand = brand,
            model = model,
            serialNumber = serialNumber,
            issueDescription = issueDescription,
            images = images.mapNotNull { image ->
                image.remoteUrl?.let { DraftImage(id = image.id, remoteUrl = it) }
            },
            selectedBranchId = selectedBranchId,
            scheduledAt = scheduledAt,
            // Recorded so the restore can say how many photos were lost
            // rather than quietly showing fewer than the customer added.
            unsavedImageCount = images.count { it.remoteUrl == null },
        )
    }

    private fun DraftImage.toBookingImage() = BookingImage(
        id = id,
        // No usable local URI after a restore; the thumbnail reads the remote
        // URL instead (see BookingImage.thumbnailModel).
        localUri = Uri.EMPTY,
        remoteUrl = remoteUrl,
        status = ImageUploadStatus.UPLOADED,
    )

    /**
     * Photos that never finished uploading cannot be restored — see
     * [DraftImage] — so the customer is told rather than left to notice a
     * missing thumbnail.
     */
    private fun restoredMessage(unsavedImages: Int): String = when (unsavedImages) {
        0 -> "Picked up where you left off."
        1 -> "Picked up where you left off. One photo hadn't finished uploading, so add it again."
        else -> "Picked up where you left off. $unsavedImages photos hadn't finished uploading, so add them again."
    }

    fun dismissDraftRestoredMessage() = _uiState.update { it.copy(draftRestoredMessage = null) }

    fun onBrandChange(value: String) = _uiState.update { it.copy(brand = value) }
    fun onModelChange(value: String) = _uiState.update { it.copy(model = value) }
    fun onSerialChange(value: String) = _uiState.update { it.copy(serialNumber = value) }
    fun onIssueChange(value: String) = _uiState.update { it.copy(issueDescription = value) }

    fun goNext() {
        val state = _uiState.value
        val canAdvance = when (state.step) {
            1 -> state.canAdvanceFromStep1
            2 -> state.canAdvanceFromStep2
            3 -> state.canAdvanceFromStep3
            else -> false
        }
        if (!canAdvance) return
        _uiState.update { it.copy(step = (it.step + 1).coerceAtMost(LAST_STEP)) }
    }

    fun goBack(onExit: () -> Unit) {
        val state = _uiState.value
        if (state.step > 1) {
            _uiState.update { it.copy(step = it.step - 1) }
        } else {
            onExit()
        }
    }

    fun onCameraPermissionDenied() {
        _uiState.update {
            it.copy(permissionDeniedMessage = "Camera permission is required to take photos.")
        }
    }

    fun dismissPermissionMessage() = _uiState.update { it.copy(permissionDeniedMessage = null) }

    fun onPhotoError(message: String) = _uiState.update { it.copy(photoMessage = message) }

    fun dismissPhotoMessage() = _uiState.update { it.copy(photoMessage = null) }

    // ------------------------------------------------------------- images

    fun addImages(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return

        uris.forEach { uri ->
            UploadDiagnostics.debug("Photo selected. URI=$uri scheme=${uri.scheme}")
        }

        val state = _uiState.value
        val existingUris = state.images.map { it.localUri }.toSet()
        val uniqueUris = uris
            .filterNot { it == Uri.EMPTY || it in existingUris }
            .distinct()
        val accepted = uniqueUris.take(state.remainingPhotoSlots)

        if (accepted.isEmpty()) {
            val message = if (state.remainingPhotoSlots == 0) {
                "You can add up to $MAX_PHOTOS photos. Remove one to choose another."
            } else {
                "That photo is already in your repair request."
            }
            _uiState.update { it.copy(photoMessage = message) }
            return
        }

        accepted.forEach { uri -> addImage(context, uri) }

        val skipped = uniqueUris.size - accepted.size
        if (skipped > 0) {
            _uiState.update {
                it.copy(photoMessage = "Added ${accepted.size} photos. You can attach up to $MAX_PHOTOS in total.")
            }
        }
    }

    private fun addImage(context: Context, uri: Uri) {
        val image = BookingImage(localUri = uri, status = ImageUploadStatus.UPLOADING)
        _uiState.update { it.copy(images = it.images + image, photoMessage = null) }
        uploadImage(context, image.id, uri)
    }

    fun retryImage(context: Context, imageId: String) {
        val image = _uiState.value.images.firstOrNull { it.id == imageId } ?: return
        UploadDiagnostics.debug(
            "Retry requested. imageId=$imageId URI=${image.localUri} scheme=${image.localUri.scheme}",
        )
        _uiState.update { state ->
            state.copy(images = state.images.map {
                if (it.id == imageId) it.copy(status = ImageUploadStatus.UPLOADING) else it
            })
        }
        uploadImage(context, imageId, image.localUri)
    }

    private fun uploadImage(context: Context, imageId: String, uri: Uri) {
        viewModelScope.launch {
            var compressed: File? = null
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val mimeType = context.contentResolver.getType(uri)
                    val originalSize = contentUriSize(context, uri)
                    UploadDiagnostics.debug(
                        "Source inspected. imageId=$imageId URI=$uri scheme=${uri.scheme} " +
                            "mimeType=$mimeType originalSize=${originalSize ?: "unknown"} " +
                            "firebaseUserId=$ownerId",
                    )
                    compressed = ImageCompressor.compress(context, uri)
                    imageUploadRepository.uploadRepairImage(ownerId, compressed!!).getOrThrow()
                }
            }.also {
                compressed?.delete()
            }
            result.exceptionOrNull()?.let { exception ->
                UploadDiagnostics.error(
                    "Photo upload pipeline failed. imageId=$imageId URI=$uri",
                    exception,
                )
            }
            _uiState.update { state ->
                state.copy(
                    images = state.images.map { image ->
                        if (image.id != imageId) return@map image
                        result.fold(
                            onSuccess = { url -> image.copy(remoteUrl = url, status = ImageUploadStatus.UPLOADED) },
                            onFailure = { image.copy(status = ImageUploadStatus.FAILED) },
                        )
                    },
                    photoMessage = result.exceptionOrNull()?.let {
                        "One photo could not be uploaded. Check the image and try again."
                    },
                )
            }
        }
    }

    private fun contentUriSize(context: Context, uri: Uri): Long? {
        if (uri.scheme == "file") return uri.path?.let(::File)?.length()?.takeIf { it >= 0L }
        var cursor: Cursor? = null
        return try {
            cursor = context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE),
                null,
                null,
                null,
            )
            if (cursor?.moveToFirst() == true) {
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else null
            } else {
                null
            }
        } catch (exception: Exception) {
            UploadDiagnostics.error("Unable to read source size. URI=$uri", exception)
            null
        } finally {
            cursor?.close()
        }
    }

    fun removeImage(imageId: String) {
        val image = _uiState.value.images.firstOrNull { it.id == imageId } ?: return
        _uiState.update { it.copy(images = it.images.filterNot { img -> img.id == imageId }) }
        val url = image.remoteUrl
        if (url != null) {
            viewModelScope.launch { imageUploadRepository.deleteRepairImage(url) }
        }
    }

    // ------------------------------------------------ location and matching

    fun hasLocationPermission(): Boolean = locationRepository.hasLocationPermission()

    /**
     * Called by the screen once the location permission prompt has resolved,
     * or immediately when permission was already held. Runs the match either
     * way — a denied permission means distance is unknown, not that the
     * customer can't book.
     */
    fun onLocationPermissionResult(granted: Boolean) {
        if (!granted) {
            _uiState.update {
                it.copy(locationStatus = LocationStatus.PERMISSION_DENIED, customerLocation = null)
            }
            runMatching(location = null)
            return
        }
        resolveLocationAndMatch()
    }

    fun retryMatching() {
        if (locationRepository.hasLocationPermission()) {
            resolveLocationAndMatch()
        } else {
            _uiState.update { it.copy(locationStatus = LocationStatus.PERMISSION_DENIED) }
            runMatching(location = null)
        }
    }

    private fun resolveLocationAndMatch() {
        _uiState.update {
            it.copy(locationStatus = LocationStatus.RESOLVING, matchLoading = true, matchError = null)
        }
        viewModelScope.launch {
            when (val result = locationRepository.currentLocation()) {
                is LocationResult.Available -> {
                    _uiState.update {
                        it.copy(
                            locationStatus = LocationStatus.AVAILABLE,
                            customerLocation = result.coordinates,
                        )
                    }
                    runMatching(result.coordinates)
                }

                LocationResult.PermissionDenied -> {
                    _uiState.update {
                        it.copy(locationStatus = LocationStatus.PERMISSION_DENIED, customerLocation = null)
                    }
                    runMatching(location = null)
                }

                LocationResult.Unavailable -> {
                    _uiState.update {
                        it.copy(locationStatus = LocationStatus.UNAVAILABLE, customerLocation = null)
                    }
                    runMatching(location = null)
                }
            }
        }
    }

    private fun runMatching(location: Coordinates?) {
        val category = _uiState.value.category
        if (category == null) {
            _uiState.update {
                it.copy(matchLoading = false, matchError = "Device category is missing — go back a step.")
            }
            return
        }
        _uiState.update { it.copy(matchLoading = true, matchError = null) }
        viewModelScope.launch {
            matchBranches(category, location)
                .onSuccess { result ->
                    _uiState.update { state ->
                        state.copy(
                            matchLoading = false,
                            matchResult = result,
                            // Preselect the recommended branch, but keep a
                            // choice the customer has already made.
                            selectedBranchId = state.selectedBranchId
                                ?.takeIf { id -> result.matches.any { it.branch.id == id } }
                                ?: result.recommended?.branch?.id,
                        )
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(
                            matchLoading = false,
                            matchError = "Unable to check branch availability. Please try again.",
                        )
                    }
                }
        }
    }

    fun onBranchSelected(branchId: String) = _uiState.update { it.copy(selectedBranchId = branchId) }

    fun onScheduleChange(epochMillis: Long) = _uiState.update { it.copy(scheduledAt = epochMillis) }

    // -------------------------------------------------------------- submit

    fun submit(onSubmitted: (String) -> Unit) {
        val state = _uiState.value
        if (!state.canSubmit) return
        val service = state.service ?: return
        val category = state.category ?: return
        val branchId = state.selectedBranchId ?: return

        _uiState.update { it.copy(submitting = true, submitError = null) }
        viewModelScope.launch {
            val request = RepairRequest(
                id = "",
                customerId = ownerId,
                serviceId = service.id,
                deviceDetails = DeviceDetails(
                    category = category,
                    brand = state.brand.trim(),
                    model = state.model.trim(),
                    serialNumber = state.serialNumber.trim().takeIf { it.isNotBlank() },
                ),
                issueDescription = state.issueDescription.trim(),
                imageUrls = state.uploadedImageUrls,
                branchId = branchId,
                // Assigning a named technician is a Branch Manager action in
                // Block 7 — matching only picks the branch.
                technicianId = null,
                status = RepairStatus.SUBMITTED,
                scheduledAt = state.scheduledAt,
            )
            repairRequestRepository.createRepairRequest(request)
                .onSuccess { requestId ->
                    // The booking is in Firestore now, so the local draft has
                    // nothing left to protect. Cleared before the callback so
                    // reopening the flow can't resurrect a submitted booking.
                    draftRepository.clear()
                    _uiState.update { it.copy(submitting = false, submittedRequestId = requestId) }
                    onSubmitted(requestId)
                }
                .onFailure {
                    _uiState.update {
                        it.copy(
                            submitting = false,
                            submitError = "Unable to submit your booking. Please try again.",
                        )
                    }
                }
        }
    }

    fun dismissSubmitError() = _uiState.update { it.copy(submitError = null) }

    companion object {
        const val LAST_STEP = 4
        const val MAX_PHOTOS = BookingPhotoPolicy.MAX_PHOTOS
        private const val DRAFT_SAVE_DEBOUNCE_MS = 400L

        /**
         * Tomorrow at 10:00 local, so the schedule field is never empty and
         * the customer only touches it to pick a different slot.
         */
        private fun defaultScheduleSlot(): Long = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        fun factory(
            serviceId: String,
            ownerId: String,
            serviceRepository: ServiceRepository,
            imageUploadRepository: ImageUploadRepository,
            locationRepository: LocationRepository,
            matchBranches: MatchBranchesUseCase,
            repairRequestRepository: RepairRequestRepository,
            draftRepository: DraftRepairRequestRepository,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    BookRepairViewModel(
                        serviceId = serviceId,
                        ownerId = ownerId,
                        serviceRepository = serviceRepository,
                        imageUploadRepository = imageUploadRepository,
                        locationRepository = locationRepository,
                        matchBranches = matchBranches,
                        repairRequestRepository = repairRequestRepository,
                        draftRepository = draftRepository,
                    ) as T
            }
    }
}
