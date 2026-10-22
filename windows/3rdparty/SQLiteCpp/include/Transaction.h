/**
 * @file    Transaction.h
 * @ingroup SQLiteCpp
 * @brief   A Transaction is way to group multiple SQL statements into an atomic secured operation.
 *
 * Copyright (c) 2012-2025 Sebastien Rombauts (sebastien.rombauts@gmail.com)
 *
 * Distributed under the MIT License (MIT) (See accompanying file LICENSE.txt
 * or copy at http://opensource.org/licenses/MIT)
 */
#pragma once

#include <3rdparty/SQLiteCpp/include/SQLiteCppExport.h>
#include <3rdparty/SQLiteCpp/include/Exception.h>

namespace SQLite
{

class Database;

enum class TransactionBehavior {
    DEFERRED,
    IMMEDIATE,
    EXCLUSIVE,
};

class SQLITECPP_API Transaction
{
public:
    explicit Transaction(Database& aDatabase);

    explicit Transaction(Database& aDatabase, TransactionBehavior behavior);

    Transaction(const Transaction&) = delete;
    Transaction& operator=(const Transaction&) = delete;

    ~Transaction();

    void commit();

    void rollback();

private:
    Database&   mDatabase;
    bool        mbCommited = false;
};

}
