package org.koitharu.kotatsu.core.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.koitharu.kotatsu.core.db.dao.AuxiliaryDao
import org.koitharu.kotatsu.core.db.dao.ChaptersDao
import org.koitharu.kotatsu.core.db.dao.ChaptersHistoryDao
import org.koitharu.kotatsu.core.db.dao.FavouritesDao
import org.koitharu.kotatsu.core.db.dao.HistoryDao
import org.koitharu.kotatsu.core.db.dao.MangaDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

	@Provides
	@Singleton
	fun provideMangaDatabase(
		@ApplicationContext context: Context,
	): MangaDatabase {
		return Room.databaseBuilder(context, MangaDatabase::class.java, "kotatsu-db")
			.setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
			.addMigrations(Migration27To28, Migration28To29)
			.fallbackToDestructiveMigration(dropAllTables = true)
			.addCallback(
				object : RoomDatabase.Callback() {
					override fun onOpen(db: SupportSQLiteDatabase) {
						super.onOpen(db)
						db.execSQL("PRAGMA synchronous = NORMAL;")
						db.execSQL("PRAGMA mmap_size = 268435456;") // 256MB memory-mapped I/O for zero-syscall reads
						db.execSQL("PRAGMA temp_store = MEMORY;")
						db.execSQL("PRAGMA cache_size = -32000;")   // 32MB page cache
					}
				},
			)
			.build()
	}

	@Provides
	fun provideMangaDao(db: MangaDatabase): MangaDao = db.getMangaDao()

	@Provides
	fun provideChaptersDao(db: MangaDatabase): ChaptersDao = db.getChaptersDao()

	@Provides
	fun provideFavouritesDao(db: MangaDatabase): FavouritesDao = db.getFavouritesDao()

	@Provides
	fun provideHistoryDao(db: MangaDatabase): HistoryDao = db.getHistoryDao()

	@Provides
	fun provideChaptersHistoryDao(db: MangaDatabase): ChaptersHistoryDao = db.getChaptersHistoryDao()

	@Provides
	fun provideAuxiliaryDao(db: MangaDatabase): AuxiliaryDao = db.getAuxiliaryDao()
}
