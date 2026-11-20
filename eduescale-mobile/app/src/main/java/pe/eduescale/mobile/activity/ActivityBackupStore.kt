package pe.eduescale.mobile.activity

interface ActivityBackupStore {
    suspend fun exportBackup(): SchoolActivityBackup
    suspend fun importBackup(backup: SchoolActivityBackup)
}
