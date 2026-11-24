package uz.selerown.fevapp.core.database

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): FevDatabase =
        Room.databaseBuilder(context, FevDatabase::class.java, FevDatabase.NAME).build()

    @Provides
    fun projectDao(db: FevDatabase): ProjectDao = db.projectDao()
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class RepositoryModule {
    @Binds
    abstract fun projectRepository(impl: RoomProjectRepository): ProjectRepository
}
