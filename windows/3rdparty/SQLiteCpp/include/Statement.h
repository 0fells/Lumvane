/**
 * @file    Statement.h
 * @ingroup SQLiteCpp
 * @brief   A prepared SQLite Statement is a compiled SQL query ready to be executed, pointing to a row of result.
 *
 * Copyright (c) 2012-2025 Sebastien Rombauts (sebastien.rombauts@gmail.com)
 *
 * Distributed under the MIT License (MIT) (See accompanying file LICENSE.txt
 * or copy at http://opensource.org/licenses/MIT)
 */
#pragma once

#include <3rdparty/SQLiteCpp/include/SQLiteCppExport.h>
#include <3rdparty/SQLiteCpp/include/Exception.h>
#include <3rdparty/SQLiteCpp/include/Utils.h>

#include <cstdint>
#include <string>
#include <map>
#include <memory>

struct sqlite3;
struct sqlite3_stmt;

namespace SQLite
{

class Database;
class Column;

SQLITECPP_API extern const int OK;

class SQLITECPP_API Statement
{
public:
    Statement(const Database& aDatabase, const char* apQuery);

    Statement(const Database& aDatabase, const std::string& aQuery) :
        Statement(aDatabase, aQuery.c_str())
    {}

    Statement(const Statement&) = delete;
    Statement& operator=(const Statement&) = delete;

    Statement(Statement&& aStatement) noexcept;
    Statement& operator=(Statement&& aStatement) = default;

    ~Statement() = default;

    void reset();

    int tryReset() noexcept;

    void clearBindings();

    SQLITECPP_PURE_FUNC
    int getIndex(const char * const apName) const;

    void bind(const int aIndex, const int32_t       aValue);
    void bind(const int aIndex, const uint32_t      aValue);
    void bind(const int aIndex, const int64_t       aValue);
    void bind(const int aIndex, const double        aValue);
    void bind(const int aIndex, const std::string&  aValue);
    void bind(const int aIndex, const char*         apValue);
    void bind(const int aIndex, const void*         apValue, const int aSize);
    void bindNoCopy(const int aIndex, const std::string&    aValue);
    void bindNoCopy(const int aIndex, const char*           apValue);
    void bindNoCopy(const int aIndex, const void*           apValue, const int aSize);
    void bindNoCopy(const int aIndex, std::string&& aValue) = delete;

    void bind(const int aIndex);

    void bind(const char* apName, const int32_t         aValue)
    {
        bind(getIndex(apName), aValue);
    }
    void bind(const char* apName, const uint32_t        aValue)
    {
        bind(getIndex(apName), aValue);
    }
    void bind(const char* apName, const int64_t         aValue)
    {
        bind(getIndex(apName), aValue);
    }
    void bind(const char* apName, const double          aValue)
    {
        bind(getIndex(apName), aValue);
    }
    void bind(const char* apName, const std::string&    aValue)
    {
        bind(getIndex(apName), aValue);
    }
    void bind(const char* apName, const char*           apValue)
    {
        bind(getIndex(apName), apValue);
    }
    void bind(const char* apName, const void*           apValue, const int aSize)
    {
        bind(getIndex(apName), apValue, aSize);
    }
    void bindNoCopy(const char* apName, const std::string&  aValue)
    {
        bindNoCopy(getIndex(apName), aValue);
    }
    void bindNoCopy(const char* apName, const char*         apValue)
    {
        bindNoCopy(getIndex(apName), apValue);
    }
    void bindNoCopy(const char* apName, const void*         apValue, const int aSize)
    {
        bindNoCopy(getIndex(apName), apValue, aSize);
    }
    void bindNoCopy(const char* apName, std::string&& aValue) = delete;
    void bind(const char* apName)
    {
        bind(getIndex(apName));
    }


    void bind(const std::string& aName, const int32_t         aValue)
    {
        bind(aName.c_str(), aValue);
    }
    void bind(const std::string& aName, const uint32_t        aValue)
    {
        bind(aName.c_str(), aValue);
    }
    void bind(const std::string& aName, const int64_t         aValue)
    {
        bind(aName.c_str(), aValue);
    }
    void bind(const std::string& aName, const double          aValue)
    {
        bind(aName.c_str(), aValue);
    }
    void bind(const std::string& aName, const std::string&    aValue)
    {
        bind(aName.c_str(), aValue);
    }
    void bind(const std::string& aName, const char*           apValue)
    {
        bind(aName.c_str(), apValue);
    }
    void bind(const std::string& aName, const void*           apValue, const int aSize)
    {
        bind(aName.c_str(), apValue, aSize);
    }
    void bindNoCopy(const std::string& aName, const std::string& aValue)
    {
        bindNoCopy(aName.c_str(), aValue);
    }
    void bindNoCopy(const std::string& aName, const char*        apValue)
    {
        bindNoCopy(aName.c_str(), apValue);
    }
    void bindNoCopy(const std::string& aName, const void*        apValue, const int aSize)
    {
        bindNoCopy(aName.c_str(), apValue, aSize);
    }
    void bindNoCopy(const std::string& aName, std::string&& aValue) = delete;
    void bind(const std::string& aName)
    {
        bind(aName.c_str());
    }

    bool executeStep();

    int tryExecuteStep() noexcept;

    int exec();

    Column  getColumn(const int aIndex) const;

    Column  getColumn(const char* apName) const;

#if __cplusplus >= 201402L || (defined(_MSC_VER) && _MSC_VER >= 1900)
    template<typename T, int N>
    T       getColumns();

private:
    template<typename T, const int... Is>
    T       getColumns(const std::integer_sequence<int, Is...>);

public:
#endif

    bool    isColumnNull(const int aIndex) const;

    bool    isColumnNull(const char* apName) const;

    const char* getColumnName(const int aIndex) const;

#ifdef SQLITE_ENABLE_COLUMN_METADATA
    const char* getColumnOriginName(const int aIndex) const;
#endif

    int getColumnIndex(const char* apName) const;


    const char * getColumnDeclaredType(const int aIndex) const;


    int getChanges() const noexcept;


    const std::string& getQuery() const
    {
        return mQuery;
    }

    std::string getExpandedSQL() const;

    int getColumnCount() const
    {
        return mColumnCount;
    }
    bool hasRow() const
    {
        return mbHasRow;
    }
    bool isDone() const
    {
        return mbDone;
    }

    int getBindParameterCount() const noexcept;

    int getErrorCode() const noexcept;
    int getExtendedErrorCode() const noexcept;
    const char* getErrorMsg() const noexcept;

    using TStatementPtr = std::shared_ptr<sqlite3_stmt>;

private:
    void check(const int aRet) const
    {
        if (SQLite::OK != aRet)
        {
            throw SQLite::Exception(mpSQLite, aRet);
        }
    }

    void checkRow() const
    {
        if (false == mbHasRow)
        {
            throw SQLite::Exception("No row to get a column from. executeStep() was not called, or returned false.");
        }
    }

    void checkIndex(const int aIndex) const
    {
        if ((aIndex < 0) || (aIndex >= mColumnCount))
        {
            throw SQLite::Exception("Column index out of range.");
        }
    }

    TStatementPtr prepareStatement();

    sqlite3_stmt* getPreparedStatement() const;

    std::string             mQuery;
    sqlite3*                mpSQLite;
    TStatementPtr           mpPreparedStatement;
    int                     mColumnCount = 0;
    bool                    mbHasRow = false;
    bool                    mbDone = false;

    mutable std::map<std::string, int>  mColumnNames;
};

}
