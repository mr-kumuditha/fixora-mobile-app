package com.techfix.app.core.data

import android.content.Context
import com.techfix.app.core.data.branch.FirestoreBranchRepository
import com.techfix.app.core.data.catalog.CachingServiceRepository
import com.techfix.app.core.data.catalog.FirestoreServiceRepository
import com.techfix.app.core.data.draft.RoomDraftRepairRequestRepository
import com.techfix.app.core.data.local.FixoraDatabase
import com.techfix.app.core.data.location.FusedLocationRepository
import com.techfix.app.core.data.payment.FirestorePaymentRepository
import com.techfix.app.core.data.repair.FirestoreRepairRequestRepository
import com.techfix.app.core.data.sparepart.SupabaseSparePartRepository
import com.techfix.app.core.data.storage.SupabaseImageUploadRepository
import com.techfix.app.domain.branch.BranchRepository
import com.techfix.app.domain.catalog.ServiceRepository
import com.techfix.app.domain.draft.DraftRepairRequestRepository
import com.techfix.app.domain.location.LocationRepository
import com.techfix.app.domain.payment.PaymentRepository
import com.techfix.app.domain.repair.RepairRequestRepository
import com.techfix.app.domain.sparepart.SparePartRepository
import com.techfix.app.domain.storage.ImageUploadRepository

/**
 * Single place the data-layer implementations are chosen, in the same
 * hand-rolled style as AuthRepositoryProvider (no DI framework — see the
 * architecture doc's scope notes). ViewModels depend on the interfaces.
 */
object RepositoryProvider {

    /**
     * Room needs a Context. [FixoraApp][com.techfix.app.FixoraApp] hands the
     * application context over before any screen composes, which is why the
     * locally-backed repositories below can stay plain `by lazy` like the
     * rest instead of taking a Context at every call site.
     */
    @Volatile
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    private val database: FixoraDatabase
        get() = FixoraDatabase.get(
            requireNotNull(appContext) {
                "RepositoryProvider.initialize() must be called from Application.onCreate()"
            },
        )

    /**
     * Firestore is the source of truth; Room only serves the last successful
     * fetch when the network read fails. The ViewModels see one interface and
     * cannot tell the difference except through `getServicesWithSource`.
     */
    val services: ServiceRepository by lazy {
        CachingServiceRepository(
            remote = FirestoreServiceRepository(),
            dao = database.serviceCacheDao(),
        )
    }

    /** Local only — a draft booking never leaves the device until it's submitted. */
    val draftRepairRequests: DraftRepairRequestRepository by lazy {
        RoomDraftRepairRequestRepository(database.draftRepairRequestDao())
    }

    val branches: BranchRepository by lazy { FirestoreBranchRepository() }
    val repairRequests: RepairRequestRepository by lazy { FirestoreRepairRequestRepository() }
    val payments: PaymentRepository by lazy { FirestorePaymentRepository() }
    val spareParts: SparePartRepository by lazy { SupabaseSparePartRepository() }
    val imageUpload: ImageUploadRepository by lazy { SupabaseImageUploadRepository() }

    /**
     * Location needs a Context, so unlike the others it can't be a plain
     * `by lazy`. Held as a single instance keyed off the application context
     * (never an Activity, which would leak it across rotation).
     */
    @Volatile
    private var locationRepository: LocationRepository? = null

    fun location(context: Context): LocationRepository =
        locationRepository ?: synchronized(this) {
            locationRepository ?: FusedLocationRepository(context.applicationContext)
                .also { locationRepository = it }
        }
}
