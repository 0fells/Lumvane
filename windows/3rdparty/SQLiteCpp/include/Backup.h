/**
 * @file    Backup.h
 * @ingroup SQLiteCpp
 * @brief   Backup is used to backup a database file in a safe and online way.
 *
 * Copyright (c) 2015 Shibao HONG (shibaohong@outlook.com)
 * Copyright (c) 2015-2025 Sebastien Rombauts (sebastien.rombauts@gmail.com)
 *
 * Distributed under the MIT License (MIT) (See accompanying file LICENSE.txt
 * or copy at http://opensource.org/licenses/MIT)
 */
#pragma once

#include <3rdparty/SQLiteCpp/include/SQLiteCppExport.h>
#include <3rdparty/SQLiteCpp/include/Database.h>

#include <string>
#include <memory>

struct sqlite3_backup;

namespace SQLite
{

class SQLITECPP_API Backup
{
public:
    Backup(Database&   aDestDatabase,
           const char* apDestDatabaseName,
           Database&   aSrcDatabase,
           const char* apSrcDatabaseName);

    Backup(Database&          aDestDatabase,
           const std::string& aDestDatabaseName,
           Database&          aSrcDatabase,
           const std::string& aSrcDatabaseName);

    Backup(Database& aDestDatabase,
           Database& aSrcDatabase);

    Backup(const Backup&) = delete;
    Backup& operator=(const Backup&) = delete;

    int executeStep(const int aNumPage = -1);

    int getRemainingPageCount() const;

    int getTotalPageCount() const;

private:
    struct Deleter
    {
        void operator()(sqlite3_backup* apBackup);
    };

    std::unique_ptr<sqlite3_backup, Deleter> mpSQLiteBackup;
};

}
