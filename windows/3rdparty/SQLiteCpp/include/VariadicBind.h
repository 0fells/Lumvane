/**
 * @file    VariadicBind.h
 * @ingroup SQLiteCpp
 * @brief   Convenience function for Statement::bind(...)
 *
 * Copyright (c) 2016 Paul Dreik (github@pauldreik.se)
 * Copyright (c) 2016-2025 Sebastien Rombauts (sebastien.rombauts@gmail.com)
 * Copyright (c) 2019 Maximilian Bachmann (contact@maxbachmann.de)
 *
 * Distributed under the MIT License (MIT) (See accompanying file LICENSE.txt
 * or copy at http://opensource.org/licenses/MIT)
 */
#pragma once

#include <3rdparty/SQLiteCpp/include/Statement.h>

#if (__cplusplus >= 201402L) || ( defined(_MSC_VER) && (_MSC_VER >= 1900) )
#include <tuple>
#endif

#include <utility>
#include <initializer_list>

namespace SQLite
{
template<class ...Args>
void bind(SQLite::Statement& query, const Args& ... args)
{
    int pos = 0;
    (void)std::initializer_list<int>{
        ((void)query.bind(++pos, std::forward<decltype(args)>(args)), 0)...
    };
}

#if (__cplusplus >= 201402L) || ( defined(_MSC_VER) && (_MSC_VER >= 1900) )

template <typename ... Types>
void bind(SQLite::Statement& query, const std::tuple<Types...> &tuple)
{
    bind(query, tuple, std::index_sequence_for<Types...>());
}

template <typename ... Types, std::size_t ... Indices>
void bind(SQLite::Statement& query, const std::tuple<Types...> &tuple, std::index_sequence<Indices...>)
{
    bind(query, std::get<Indices>(tuple)...);
}
#endif

}
