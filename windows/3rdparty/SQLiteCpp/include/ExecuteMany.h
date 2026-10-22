/**
 * @file    ExecuteMany.h
 * @ingroup SQLiteCpp
 * @brief   Convenience function to execute a Statement with multiple Parameter sets
 *
 * Copyright (c) 2019 Maximilian Bachmann (contact@maxbachmann.de)
 * Copyright (c) 2019-2025 Sebastien Rombauts (sebastien.rombauts@gmail.com)
 *
 * Distributed under the MIT License (MIT) (See accompanying file LICENSE.txt
 * or copy at http://opensource.org/licenses/MIT)
 */
#pragma once

#if (__cplusplus >= 201402L) || ( defined(_MSC_VER) && (_MSC_VER >= 1900) )

#include <3rdparty/SQLiteCpp/include/Statement.h>
#include <3rdparty/SQLiteCpp/include/VariadicBind.h>

#include <tuple>
#include <utility>
#include <initializer_list>

namespace SQLite
{

template <typename Arg, typename... Types>
void execute_many(Database& aDatabase, const char* apQuery, Arg&& aArg, Types&&... aParams)
{
    SQLite::Statement query(aDatabase, apQuery);
    bind_exec(query, std::forward<Arg>(aArg));
    (void)std::initializer_list<int>
    {
        ((void)reset_bind_exec(query, std::forward<Types>(aParams)), 0)...
    };
}

template <typename TupleT>
void reset_bind_exec(Statement& apQuery, TupleT&& aTuple)
{
    apQuery.reset();
    bind_exec(apQuery, std::forward<TupleT>(aTuple));
}

template <typename TupleT>
void bind_exec(Statement& apQuery, TupleT&& aTuple)
{
    SQLite::bind(apQuery, std::forward<TupleT>(aTuple));
    while (apQuery.executeStep()) {}
}

}

#endif
