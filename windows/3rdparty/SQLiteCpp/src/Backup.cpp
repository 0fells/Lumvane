/**
 * @file    Backup.cpp
 * @ingroup SQLiteCpp
 * @brief   Backup is used to backup a database file in a safe and online way.
 *
 * Copyright (c) 2015 Shibao HONG (shibaohong@outlook.com)
 * Copyright (c) 2015-2025 Sebastien Rombauts (sebastien.rombauts@gmail.com)
 *
 * Distributed under the MIT License (MIT) (See accompanying file LICENSE.txt
 * or copy at http://opensource.org/licenses/MIT)
 */
#include <3rdparty/SQLiteCpp/include/Backup.h>

#include <3rdparty/SQLiteCpp/include/Exception.h>

#include <3rdparty/SQLiteCpp/include/sqlite3.h>

namespace SQLite
{

Backup::Backup(Database&    aDestDatabase,
               const char*  apDestDatabaseName,
               Database&    aSrcDatabase,
               const char*  apSrcDatabaseName)
{
    mpSQLiteBackup.reset(sqlite3_backup_init(aDestDatabase.getHandle(),
                                         apDestDatabaseName,
                                         aSrcDatabase.getHandle(),
                                         apSrcDatabaseName));
    if (nullptr == mpSQLiteBackup)
    {
        throw SQLite::Exception(aDestDatabase.getHandle());
    }
}

Backup::Backup(Database&            aDestDatabase,
               const std::string&   aDestDatabaseName,
               Database&            aSrcDatabase,
               const std::string&   aSrcDatabaseName) :
    Backup(aDestDatabase, aDestDatabaseName.c_str(), aSrcDatabase, aSrcDatabaseName.c_str())
{
}

Backup::Backup(Database &aDestDatabase, Database &aSrcDatabase) :
    Backup(aDestDatabase, "main", aSrcDatabase, "main")
{
}

int Backup::executeStep(const int aNumPage)
{
    const int res = sqlite3_backup_step(mpSQLiteBackup.get(), aNumPage);
    if (SQLITE_OK != res && SQLITE_DONE != res && SQLITE_BUSY != res && SQLITE_LOCKED != res)
    {
        throw SQLite::Exception(sqlite3_errstr(res), res);
    }
    return res;
}

int Backup::getRemainingPageCount() const
{
    return sqlite3_backup_remaining(mpSQLiteBackup.get());
}

int Backup::getTotalPageCount() const
{
    return sqlite3_backup_pagecount(mpSQLiteBackup.get());
}

void SQLite::Backup::Deleter::operator()(sqlite3_backup* apBackup)
{
    if (apBackup)
    {
        sqlite3_backup_finish(apBackup);
    }
}

}
