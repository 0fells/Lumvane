/**
 * @file    Exception.h
 * @ingroup SQLiteCpp
 * @brief   Encapsulation of the error message from SQLite3 on a std::runtime_error.
 *
 * Copyright (c) 2012-2025 Sebastien Rombauts (sebastien.rombauts@gmail.com)
 *
 * Distributed under the MIT License (MIT) (See accompanying file LICENSE.txt
 * or copy at http://opensource.org/licenses/MIT)
 */
#pragma once

#include <3rdparty/SQLiteCpp/include/SQLiteCppExport.h>
#include <stdexcept>
#include <string>

struct sqlite3;

namespace SQLite
{

class SQLITECPP_API Exception : public std::runtime_error
{
public:
    Exception(const char* aErrorMessage, int ret);

    Exception(const std::string& aErrorMessage, int ret) :
        Exception(aErrorMessage.c_str(), ret)
    {
    }

    explicit Exception(const char* aErrorMessage) :
        Exception(aErrorMessage, -1)
    {
    }
    explicit Exception(const std::string& aErrorMessage) :
        Exception(aErrorMessage.c_str(), -1)
    {
    }

    explicit Exception(sqlite3* apSQLite);

    Exception(sqlite3* apSQLite, int ret);

    int getErrorCode() const noexcept
    {
        return mErrcode;
    }

    int getExtendedErrorCode() const noexcept
    {
        return mExtendedErrcode;
    }

    const char* getErrorStr() const noexcept;

private:
    int mErrcode;
    int mExtendedErrcode;
};

}
