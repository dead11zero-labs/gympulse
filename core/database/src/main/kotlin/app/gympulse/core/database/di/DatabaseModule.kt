package app.gympulse.core.database.di

import android.content.Context
import androidx.room.Room
import app.gympulse.core.database.GymPulseDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GymPulseDatabase =
        Room.databaseBuilder(context, GymPulseDatabase::class.java, GymPulseDatabase.NAME).build()

    @Provides fun provideExerciseDao(db: GymPulseDatabase) = db.exerciseDao()
    @Provides fun provideEquipmentDao(db: GymPulseDatabase) = db.equipmentDao()
    @Provides fun providePlanDao(db: GymPulseDatabase) = db.planDao()
    @Provides fun provideScheduleDao(db: GymPulseDatabase) = db.scheduleDao()
    @Provides fun provideSessionDao(db: GymPulseDatabase) = db.sessionDao()
    @Provides fun provideBodyDao(db: GymPulseDatabase) = db.bodyDao()
    @Provides fun providePersonalRecordDao(db: GymPulseDatabase) = db.personalRecordDao()
}
