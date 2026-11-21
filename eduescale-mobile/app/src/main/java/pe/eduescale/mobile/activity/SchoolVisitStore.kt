package pe.eduescale.mobile.activity

interface SchoolVisitStore {
    suspend fun visitsFor(schoolKey: String): List<SchoolVisit>
    suspend fun allVisits(): List<SchoolVisit>

    suspend fun markAsVisited(
        schoolKey: String,
        schoolName: String,
        visitDate: Long = System.currentTimeMillis(),
        observations: String? = null,
    ): SchoolVisit

    suspend fun scheduleVisit(
        schoolKey: String,
        schoolName: String,
        scheduledDate: Long,
    ): SchoolVisit

    suspend fun reportPermissionDenied(
        schoolKey: String,
        schoolName: String,
        reason: String,
    ): SchoolVisit

    suspend fun visitsByStatus(status: VisitStatus): List<SchoolVisit>
}
