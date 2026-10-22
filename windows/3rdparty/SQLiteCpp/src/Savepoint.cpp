/**
 * @file    Savepoint.cpp
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

#include <3rdparty/SQLiteCpp/include/Assertion.h>
#include <3rdparty/SQLiteCpp/include/Database.h>
#include <3rdparty/SQLiteCpp/include/Savepoint.h>
#include <3rdparty/SQLiteCpp/include/Statement.h>

namespace SQLite
{

Savepoint::Savepoint(Database& aDatabase, const std::string& aName)
    : mDatabase(aDatabase), msName(aName)
{
    Statement stmt(mDatabase, "SELECT quote(?)");
    stmt.bind(1, msName);
    stmt.executeStep();
    msName = stmt.getColumn(0).getText();

    mDatabase.exec(std::string("SAVEPOINT ") + msName);
}

Savepoint::~Savepoint()
{
    if (!mbReleased)
    {
        try
        {
            rollback();
            release();
        }
        catch (SQLite::Exception&)
        {
        }
    }
}

void Savepoint::release()
{
    if (!mbReleased)
    {
        mDatabase.exec(std::string("RELEASE SAVEPOINT ") + msName);
        mbReleased = true;
    }
    else
    {
        throw SQLite::Exception("Savepoint already released.");
    }
}

void Savepoint::rollbackTo()
{
    if (!mbReleased)
    {
        mDatabase.exec(std::string("ROLLBACK TO SAVEPOINT ") + msName);
    }
    else
    {
        throw SQLite::Exception("Savepoint already released.");
    }
}

}
