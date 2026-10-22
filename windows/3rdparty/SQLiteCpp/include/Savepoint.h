/**
 * @file    Savepoint.h
 * @ingroup SQLiteCpp
 * @brief   A Savepoint is a way to group multiple SQL statements into an atomic
 * secured operation. Similar to a transaction while allowing child savepoints.
 *
 * Copyright (c) 2020 Kelvin Hammond (hammond.kelvin@gmail.com)
 * Copyright (c) 2020-2025 Sebastien Rombauts (sebastien.rombauts@gmail.com)
 *
 * Distributed under the MIT License (MIT) (See accompanying file LICENSE.txt or
 * copy at http://opensource.org/licenses/MIT)
 */
#pragma once

#include <3rdparty/SQLiteCpp/include/SQLiteCppExport.h>
#include <3rdparty/SQLiteCpp/include/Exception.h>

namespace SQLite
{

class Database;

class SQLITECPP_API Savepoint
{
public:
    Savepoint(Database& aDatabase, const std::string& aName);

    Savepoint(const Savepoint&) = delete;
    Savepoint& operator=(const Savepoint&) = delete;

    ~Savepoint();

    void release();

    void rollbackTo();
    void rollback() { rollbackTo(); }

private:
    Database&   mDatabase;
    std::string msName;
    bool        mbReleased = false;
};

}
