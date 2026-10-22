/**
 * @file    Column.cpp
 * @ingroup SQLiteCpp
 * @brief   Encapsulation of a Column in a row of the result pointed by the prepared SQLite::Statement.
 *
 * Copyright (c) 2012-2025 Sebastien Rombauts (sebastien.rombauts@gmail.com)
 *
 * Distributed under the MIT License (MIT) (See accompanying file LICENSE.txt
 * or copy at http://opensource.org/licenses/MIT)
 */
#include <3rdparty/SQLiteCpp/include/Column.h>

#include <3rdparty/SQLiteCpp/include/sqlite3.h>

#include <iostream>

namespace SQLite
{

const int INTEGER   = SQLITE_INTEGER;
const int FLOAT     = SQLITE_FLOAT;
const int TEXT      = SQLITE_TEXT;
const int BLOB      = SQLITE_BLOB;
const int Null      = SQLITE_NULL;


Column::Column(const Statement::TStatementPtr& aStmtPtr, int aIndex) :
    mStmtPtr(aStmtPtr),
    mIndex(aIndex)
{
    if (!aStmtPtr)
    {
        throw SQLite::Exception("Statement was destroyed");
    }
}

const char* Column::getName() const noexcept
{
    return sqlite3_column_name(mStmtPtr.get(), mIndex);
}

#ifdef SQLITE_ENABLE_COLUMN_METADATA
const char* Column::getOriginName() const noexcept
{
    return sqlite3_column_origin_name(mStmtPtr.get(), mIndex);
}
#endif

int32_t Column::getInt() const noexcept
{
    return sqlite3_column_int(mStmtPtr.get(), mIndex);
}

uint32_t Column::getUInt() const noexcept
{
    return static_cast<unsigned>(getInt64());
}

int64_t Column::getInt64() const noexcept
{
    return sqlite3_column_int64(mStmtPtr.get(), mIndex);
}

double Column::getDouble() const noexcept
{
    return sqlite3_column_double(mStmtPtr.get(), mIndex);
}

const char* Column::getText(const char* apDefaultValue) const noexcept
{
    auto pText = reinterpret_cast<const char*>(sqlite3_column_text(mStmtPtr.get(), mIndex));
    return (pText ? pText : apDefaultValue);
}

const void* Column::getBlob() const noexcept
{
    return sqlite3_column_blob(mStmtPtr.get(), mIndex);
}

std::string Column::getString() const
{
    (void)sqlite3_column_bytes(mStmtPtr.get(), mIndex);
    auto data = static_cast<const char *>(sqlite3_column_blob(mStmtPtr.get(), mIndex));

    return std::string(data, sqlite3_column_bytes(mStmtPtr.get(), mIndex));
}

int Column::getType() const noexcept
{
    return sqlite3_column_type(mStmtPtr.get(), mIndex);
}

int Column::getBytes() const noexcept
{
    return sqlite3_column_bytes(mStmtPtr.get(), mIndex);
}

std::ostream& operator<<(std::ostream& aStream, const Column& aColumn)
{
    aStream.write(aColumn.getText(), aColumn.getBytes());
    return aStream;
}

}
