/**
 * @file    Column.h
 * @ingroup SQLiteCpp
 * @brief   Encapsulation of a Column in a row of the result pointed by the prepared SQLite::Statement.
 *
 * Copyright (c) 2012-2025 Sebastien Rombauts (sebastien.rombauts@gmail.com)
 *
 * Distributed under the MIT License (MIT) (See accompanying file LICENSE.txt
 * or copy at http://opensource.org/licenses/MIT)
 */
#pragma once

#include <3rdparty/SQLiteCpp/include/SQLiteCppExport.h>
#include <3rdparty/SQLiteCpp/include/Statement.h>
#include <3rdparty/SQLiteCpp/include/Exception.h>

#include <string>
#include <memory>

struct sqlite3_stmt;

namespace SQLite
{

SQLITECPP_API extern const int INTEGER;
SQLITECPP_API extern const int FLOAT;
SQLITECPP_API extern const int TEXT;
SQLITECPP_API extern const int BLOB;
SQLITECPP_API extern const int Null;

class SQLITECPP_API Column
{
public:
    explicit Column(const Statement::TStatementPtr& aStmtPtr, int aIndex);

    const char* getName() const noexcept;

#ifdef SQLITE_ENABLE_COLUMN_METADATA
    const char* getOriginName() const noexcept;
#endif

    int32_t     getInt() const noexcept;
    uint32_t    getUInt() const noexcept;
    int64_t     getInt64() const noexcept;
    double      getDouble() const noexcept;
    const char* getText(const char* apDefaultValue = "") const noexcept;
    const void* getBlob() const noexcept;
    std::string getString() const;

    int getType() const noexcept;

    bool isInteger() const noexcept
    {
        return (SQLite::INTEGER == getType());
    }
    bool isFloat() const noexcept
    {
        return (SQLite::FLOAT == getType());
    }
    bool isText() const noexcept
    {
        return (SQLite::TEXT == getType());
    }
    bool isBlob() const noexcept
    {
        return (SQLite::BLOB == getType());
    }
    bool isNull() const noexcept
    {
        return (SQLite::Null == getType());
    }

    int getBytes() const noexcept;

    int size() const noexcept
    {
        return getBytes ();
    }

    operator char() const
    {
        return static_cast<char>(getInt());
    }
    operator int8_t() const
    {
        return static_cast<int8_t>(getInt());
    }
    operator uint8_t() const
    {
        return static_cast<uint8_t>(getInt());
    }
    operator int16_t() const
    {
        return static_cast<int16_t>(getInt());
    }
    operator uint16_t() const
    {
        return static_cast<uint16_t>(getInt());
    }
    operator int32_t() const
    {
        return getInt();
    }
    operator uint32_t() const
    {
        return getUInt();
    }
    operator int64_t() const
    {
        return getInt64();
    }
    operator double() const
    {
        return getDouble();
    }
    operator const char*() const
    {
        return getText();
    }
    operator const void*() const
    {
        return getBlob();
    }

    operator std::string() const
    {
        return getString();
    }

private:
    Statement::TStatementPtr    mStmtPtr;
    int                         mIndex;
};

SQLITECPP_API std::ostream& operator<<(std::ostream& aStream, const Column& aColumn);

#if __cplusplus >= 201402L || (defined(_MSC_VER) && _MSC_VER >= 1900)

template<typename T, int N>
T Statement::getColumns()
{
    checkRow();
    checkIndex(N - 1);
    return getColumns<T>(std::make_integer_sequence<int, N>{});
}

template<typename T, const int... Is>
T Statement::getColumns(const std::integer_sequence<int, Is...>)
{
    return T{Column(mpPreparedStatement, Is)...};
}

#endif

}
