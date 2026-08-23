package com.techfix.app.domain.branch

interface BranchRepository {
    suspend fun getBranches(): Result<List<Branch>>
    suspend fun getBranch(branchId: String): Result<Branch>
}
